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
package io.gravitee.node.api.certificate;

import java.util.function.Consumer;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface KeyStoreLoader {
    String CERTIFICATE_FORMAT_JKS = "JKS";
    String CERTIFICATE_FORMAT_PEM = "PEM";
    String CERTIFICATE_FORMAT_PKCS12 = "PKCS12";
    String CERTIFICATE_FORMAT_SELF_SIGNED = "SELF-SIGNED";
    String CERTIFICATE_FORMAT_PEM_REGISTRY = "PEM-REGISTRY";
    String GRAVITEEIO_PEM_REGISTRY = "graviteeio-pem-registry";

    void start();

    void stop();

    void addListener(Consumer<KeyStoreBundle> listener);
}
