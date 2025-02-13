/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.node.license;

import static io.gravitee.node.api.license.License.REFERENCE_ID_PLATFORM;
import static io.gravitee.node.api.license.License.REFERENCE_TYPE_PLATFORM;

import io.gravitee.common.service.AbstractService;
import io.gravitee.node.api.Node;
import io.gravitee.node.api.configuration.Configuration;
import io.gravitee.node.api.license.*;
import io.gravitee.node.license.management.NodeLicenseManagementEndpoint;
import io.gravitee.node.management.http.endpoint.ManagementEndpointManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * @author David Brassely (david.brassely at graviteesource.com)
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class LicenseLoaderService extends AbstractService<LicenseLoaderService> {

    static final String GRAVITEE_HOME_PROPERTY = "gravitee.home";
    static final String GRAVITEE_LICENSE_KEY = "license.key";
    static final String GRAVITEE_LICENSE_PROPERTY = "gravitee.license";

    private final Node node;
    private final Configuration configuration;
    private final LicenseFactory licenseFactory;
    private final LicenseManager licenseManager;
    private final ManagementEndpointManager managementEndpointManager;

    private LicenseWatcher licenseWatcher;
    private License license;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.loadLicense();
        this.startLicenseWatcher();
        this.licenseManager.onLicenseExpires(this::onLicenseExpires);
        this.managementEndpointManager.register(new NodeLicenseManagementEndpoint(licenseManager));
    }

    private void onLicenseExpires(License license) {
        if (license == this.license) {
            stopNode();
        }
    }

    private void startLicenseWatcher() {
        String licenseKey = configuration.getProperty(GRAVITEE_LICENSE_KEY);
        if (!StringUtils.hasLength(licenseKey)) {
            licenseWatcher = new LicenseWatcher(new File(getLicenseFile()));
            licenseWatcher.setName("gravitee-license-watcher");
            licenseWatcher.start();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (licenseWatcher != null) {
            licenseWatcher.close();
        }
    }

    @Override
    protected String name() {
        return "License service";
    }

    private void loadLicense() {
        final License license = readLicense();

        if (license != null) {
            printLicenseInfo(license);
            licenseManager.registerPlatformLicense(license);
            this.license = license;
        }
    }

    private License readLicense() {
        License license = null;
        try {
            license = readLicenseLocally();
        } catch (MalformedLicenseException mle) {
            log.warn("Provided license is malformed, skipping.", mle);
        } catch (InvalidLicenseException lie) {
            log.error("Provided license is invalid, stopping.", lie);
            stopNode();
        }

        return license;
    }

    private License readLicenseLocally() throws InvalidLicenseException, MalformedLicenseException {
        final String licenseKey = configuration.getProperty(GRAVITEE_LICENSE_KEY);

        if (StringUtils.hasLength(licenseKey)) {
            return licenseFactory.create(REFERENCE_TYPE_PLATFORM, REFERENCE_ID_PLATFORM, Base64.getDecoder().decode(licenseKey));
        }

        try {
            final String licenseFile = getLicenseFile();
            return licenseFactory.create(REFERENCE_TYPE_PLATFORM, REFERENCE_ID_PLATFORM, Files.readAllBytes(Paths.get(licenseFile)));
        } catch (IOException e) {
            log.info("No license file found. Some plugins may be disabled");
        }

        return null;
    }

    private String getLicenseFile() {
        String licenseFile = System.getProperty(GRAVITEE_LICENSE_PROPERTY);

        if (licenseFile == null || licenseFile.isEmpty()) {
            licenseFile = System.getProperty(GRAVITEE_HOME_PROPERTY) + File.separator + "license" + File.separator + GRAVITEE_LICENSE_KEY;
        }

        return licenseFile;
    }

    private void printLicenseInfo(License license) {
        final StringBuilder sb = new StringBuilder();
        sb.append("License information: \n");
        license.getRawAttributes().forEach((name, feature) -> sb.append("\t").append(name).append(": ").append(feature).append("\n"));
        log.info(sb.toString());
    }

    private class LicenseWatcher extends Thread {

        private final File file;
        private final AtomicBoolean stop = new AtomicBoolean(false);

        LicenseWatcher(File file) {
            this.file = file;
        }

        @Override
        public void run() {
            log.debug("Watching license for next changes: {}", file.getAbsolutePath());

            try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
                Path path = file.toPath().getParent();
                path.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
                while (!stop.get()) {
                    WatchKey watchKey;
                    try {
                        watchKey = watcher.poll(25, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (watchKey == null) {
                        Thread.yield();
                        continue;
                    }

                    for (WatchEvent<?> event : watchKey.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path filename = ev.context();

                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            Thread.yield();
                            continue;
                        } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY && filename.toString().equals(file.getName())) {
                            LicenseLoaderService.this.loadLicense();
                        }
                        boolean valid = watchKey.reset();
                        if (!valid) {
                            break;
                        }
                    }
                    Thread.yield();
                }
            } catch (Exception e) {
                log.debug("An error occurred while watching license file", e);
            }
        }

        void close() {
            stop.set(true);
        }
    }

    private void stopNode() {
        try {
            node.stop();
        } catch (Exception e) {
            log.warn("Fail to stop node", e);
        }
        System.exit(0);
    }
}
