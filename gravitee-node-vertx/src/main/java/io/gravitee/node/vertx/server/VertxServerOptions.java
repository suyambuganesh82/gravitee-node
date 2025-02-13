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
package io.gravitee.node.vertx.server;

import io.gravitee.common.utils.UUID;
import io.gravitee.node.api.certificate.CertificateOptions;
import io.gravitee.node.api.certificate.KeyStoreLoader;
import io.gravitee.node.api.certificate.KeyStoreLoaderOptions;
import io.gravitee.node.api.server.ServerOptions;
import io.gravitee.node.certificates.KeyStoreLoaderManager;
import io.gravitee.node.vertx.cert.VertxKeyStoreManager;
import io.gravitee.node.vertx.server.http.VertxHttpServerOptions;
import io.gravitee.node.vertx.server.tcp.VertxTcpServerOptions;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.core.env.Environment;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VertxServerOptions implements ServerOptions {

    public static final boolean DEFAULT_SECURED = false;
    public static final boolean DEFAULT_SNI = false;
    public static final boolean DEFAULT_OPENSSL = false;
    public static final boolean DEFAULT_TCP_KEEP_ALIVE = true;
    public static final String CERTIFICATE_FORMAT_JKS = "JKS";
    public static final String CERTIFICATE_FORMAT_PEM = "PEM";
    public static final String CERTIFICATE_FORMAT_PKCS12 = "PKCS12";
    public static final String CERTIFICATE_FORMAT_SELF_SIGNED = "SELF-SIGNED";
    public static final String DEFAULT_STORE_TYPE = CERTIFICATE_FORMAT_JKS;
    public static final boolean DEFAULT_STORE_WATCH = true;
    public static final String DEFAULT_LISTENING_HOST = "0.0.0.0";
    public static final int DEFAULT_PORT = 8080;
    public static final int TCP_DEFAULT_PORT = 4080;
    public static final int DEFAULT_IDLE_TIMEOUT = TCPSSLOptions.DEFAULT_IDLE_TIMEOUT;
    public static final String DEFAULT_CLIENT_AUTH = ClientAuth.NONE.name();
    public static final boolean DEFAULT_HAPROXY_PROTOCOL = false;
    public static final long DEFAULT_HAPROXY_PROTOCOL_TIMEOUT = NetServerOptions.DEFAULT_PROXY_PROTOCOL_TIMEOUT;

    @Builder.Default
    protected int port = DEFAULT_PORT;

    @Builder.Default
    protected String host = DEFAULT_LISTENING_HOST;

    @Builder.Default
    protected boolean secured = DEFAULT_SECURED;

    @Builder.Default
    protected boolean sni = DEFAULT_SNI;

    @Builder.Default
    protected boolean openssl = DEFAULT_OPENSSL;

    @Builder.Default
    protected int idleTimeout = DEFAULT_IDLE_TIMEOUT;

    @Builder.Default
    protected boolean tcpKeepAlive = DEFAULT_TCP_KEEP_ALIVE;

    protected String tlsProtocols;
    protected String keyStorePath;
    protected List<String> keyStoreKubernetes;
    protected String keyStoreSecret;
    protected String keyStoreDefaultAlias;
    protected String keyStorePassword;

    @Builder.Default
    protected String keyStoreType = DEFAULT_STORE_TYPE;

    protected List<CertificateOptions> keyStoreCertificates;

    @Builder.Default
    protected boolean keyStoreWatch = DEFAULT_STORE_WATCH;

    protected String trustStorePath;
    protected String trustStorePassword;

    @Builder.Default
    protected String trustStoreType = DEFAULT_STORE_TYPE;

    protected List<String> trustStorePaths;

    @Builder.Default
    protected String clientAuth = DEFAULT_CLIENT_AUTH;

    protected List<String> authorizedTlsCipherSuites;
    protected KeyStoreLoaderManager keyStoreLoaderManager;

    @Builder.Default
    protected boolean haProxyProtocol = DEFAULT_HAPROXY_PROTOCOL;

    @Builder.Default
    protected long haProxyProtocolTimeout = DEFAULT_HAPROXY_PROTOCOL_TIMEOUT;

    protected String id;
    protected String prefix;
    protected Environment environment;

    public static VertxServerOptionsBuilder<?, ?> builder() {
        return VertxHttpServerOptions.builder();
    }

    public static VertxServerOptionsBuilder<?, ?> builder(
        Environment environment,
        String prefix,
        KeyStoreLoaderManager keyStoreLoaderManager
    ) {
        final String type = environment.getProperty(prefix + ".type");
        if (type == null || "http".equals(type)) {
            return VertxHttpServerOptions.builder().prefix(prefix).environment(environment).keyStoreLoaderManager(keyStoreLoaderManager);
        } else if ("tcp".equals(type)) {
            return VertxTcpServerOptions
                .builder()
                .prefix(prefix)
                .environment(environment)
                .keyStoreLoaderManager(keyStoreLoaderManager)
                .defaultPort(TCP_DEFAULT_PORT);
        } else {
            throw new IllegalArgumentException("Server type [" + type + "] is not supported");
        }
    }

    public abstract static class VertxServerOptionsBuilder<
        C extends VertxServerOptions, B extends VertxServerOptions.VertxServerOptionsBuilder<C, B>
    > {

        protected int defaultPort = DEFAULT_PORT;
        protected String prefix;
        protected Environment environment;

        /**
         * Change the default port to be used when no port has been specified, either by calling {@link #port(int)} either by providing it through environment variable.
         * By default, when no port is specified (neither by environment neither by calling {@link #port(int)}), the {@link #DEFAULT_PORT} constant is used.
         * Calling this method allows to change the default port value.
         *
         * @param defaultPort the default port to used if no port is explicitly specified.
         * @return the builder itself to allow fluent chaining.
         */
        public B defaultPort(int defaultPort) {
            this.defaultPort = defaultPort;

            if (!port$set || port$value == DEFAULT_PORT) {
                this.port(defaultPort);
            }

            return self();
        }

        public B prefix(String prefix) {
            this.prefix = prefix;
            return self();
        }

        public B environment(Environment environment) {
            if (prefix == null) {
                throw new IllegalArgumentException("Prefix must be set before environment");
            }
            this.environment = environment;

            this.id(environment.getProperty(prefix + ".id", UUID.random().toString()));
            this.port(Integer.parseInt(environment.getProperty(prefix + ".port", String.valueOf(defaultPort))));
            this.host(environment.getProperty(prefix + ".host", DEFAULT_LISTENING_HOST));

            this.idleTimeout(environment.getProperty(prefix + ".idleTimeout", Integer.class, DEFAULT_IDLE_TIMEOUT));
            this.tcpKeepAlive(environment.getProperty(prefix + ".tcpKeepAlive", Boolean.class, DEFAULT_TCP_KEEP_ALIVE));

            this.secured(environment.getProperty(prefix + ".secured", Boolean.class, DEFAULT_SECURED));
            this.sni(environment.getProperty(prefix + ".ssl.sni", Boolean.class, DEFAULT_SNI));
            this.openssl(environment.getProperty(prefix + ".ssl.openssl", Boolean.class, DEFAULT_OPENSSL));
            this.tlsProtocols(environment.getProperty(prefix + ".ssl.tlsProtocols"));
            this.authorizedTlsCipherSuites(environment.getProperty(prefix + ".ssl.tlsCiphers", List.class));

            final String clientAuthValue = environment.getProperty(prefix + ".ssl.clientAuth", DEFAULT_CLIENT_AUTH).toUpperCase();

            if (clientAuthValue.equalsIgnoreCase(Boolean.TRUE.toString())) {
                // On older version, clientAuth was a boolean (see https://github.com/gravitee-io/gravitee-gateway/blob/1.25.0/gravitee-gateway-standalone/gravitee-gateway-standalone-distribution/src/main/resources/config/gravitee.yml#L27)
                this.clientAuth(ClientAuth.REQUIRED.name());
            } else if (clientAuthValue.equalsIgnoreCase(Boolean.FALSE.toString())) {
                this.clientAuth(ClientAuth.NONE.name());
            } else {
                // Make sure client auth is a valid value.
                this.clientAuth(ClientAuth.valueOf(clientAuthValue.toUpperCase()).name());
            }

            this.keyStoreType(environment.getProperty(prefix + ".ssl.keystore.type", DEFAULT_STORE_TYPE));
            this.keyStorePath(environment.getProperty(prefix + ".ssl.keystore.path"));
            this.keyStoreCertificates(getCertificateValues(prefix + ".ssl.keystore.certificates"));
            this.keyStoreKubernetes(getArrayValues(prefix + ".ssl.keystore.kubernetes"));
            this.keyStoreSecret(environment.getProperty(prefix + ".ssl.keystore.secret"));
            this.keyStoreDefaultAlias(environment.getProperty(prefix + ".ssl.keystore.defaultAlias"));
            this.keyStorePassword(environment.getProperty(prefix + ".ssl.keystore.password"));
            this.keyStoreWatch(environment.getProperty(prefix + ".ssl.keystore.watch", Boolean.class, DEFAULT_STORE_WATCH));

            this.trustStoreType(environment.getProperty(prefix + ".ssl.truststore.type", DEFAULT_STORE_TYPE));
            this.trustStorePath(environment.getProperty(prefix + ".ssl.truststore.path"));
            this.trustStorePaths(getArrayValues(prefix + ".ssl.truststore.path"));
            this.trustStorePassword(environment.getProperty(prefix + ".ssl.truststore.password"));

            this.haProxyProtocol(environment.getProperty(prefix + ".haproxy.proxyProtocol", Boolean.class, DEFAULT_HAPROXY_PROTOCOL));
            this.haProxyProtocolTimeout(
                    environment.getProperty(prefix + ".haproxy.proxyProtocolTimeout", Long.class, DEFAULT_HAPROXY_PROTOCOL_TIMEOUT)
                );

            return self();
        }

        protected List<CertificateOptions> getCertificateValues(String prefix) {
            final List<CertificateOptions> certificates = new ArrayList<>();

            boolean found = true;
            int idx = 0;

            while (found) {
                final String cert = environment.getProperty(prefix + '[' + idx + "].cert");
                found = (cert != null && !cert.isEmpty());

                if (found) {
                    certificates.add(new CertificateOptions(cert, environment.getProperty(prefix + '[' + idx + "].key")));
                }

                idx++;
            }

            return certificates;
        }

        protected List<String> getArrayValues(String prefix) {
            final List<String> values = new ArrayList<>();

            boolean found = true;
            int idx = 0;

            while (found) {
                String value = environment.getProperty(prefix + '[' + idx++ + ']');
                found = (value != null && !value.isEmpty());

                if (found) {
                    values.add(value);
                }
            }

            if (values.isEmpty()) {
                // Check for a single value
                final String single = environment.getProperty(prefix);
                if (single != null && !single.isEmpty()) {
                    values.add(single);
                }
            }

            return values;
        }
    }

    protected final void setupTcp(TCPSSLOptions options) {
        if (this.secured) {
            if (keyStoreLoaderManager == null) {
                throw new IllegalArgumentException("You must provide a KeyStoreLoaderManager when 'secured' is enabled.");
            }

            if (openssl) {
                options.setSslEngineOptions(new OpenSSLEngineOptions());
            }

            options.setSsl(secured);

            // TLS protocol support
            if (tlsProtocols != null) {
                options.setEnabledSecureTransportProtocols(
                    Arrays.stream(tlsProtocols.split(",")).map(String::trim).collect(Collectors.toSet())
                );
            }

            // Restrict the authorized ciphers
            if (authorizedTlsCipherSuites != null) {
                authorizedTlsCipherSuites.stream().map(String::trim).forEach(options::addEnabledCipherSuite);
            }

            if (trustStorePaths != null && !trustStorePaths.isEmpty()) {
                if (trustStoreType == null || trustStoreType.isEmpty() || trustStoreType.equalsIgnoreCase(CERTIFICATE_FORMAT_JKS)) {
                    options.setTrustStoreOptions(new JksOptions().setPath(trustStorePaths.get(0)).setPassword(trustStorePassword));
                } else if (trustStoreType.equalsIgnoreCase(CERTIFICATE_FORMAT_PEM)) {
                    final PemTrustOptions pemTrustOptions = new PemTrustOptions();
                    trustStorePaths.forEach(pemTrustOptions::addCertPath);
                    options.setPemTrustOptions(pemTrustOptions);
                } else if (trustStoreType.equalsIgnoreCase(CERTIFICATE_FORMAT_PKCS12)) {
                    options.setPfxTrustOptions(new PfxOptions().setPath(trustStorePaths.get(0)).setPassword(trustStorePassword));
                }
            }

            final KeyStoreLoaderOptions keyStoreLoaderOptions = KeyStoreLoaderOptions
                .builder()
                .withKeyStorePath(keyStorePath)
                .withKeyStorePassword(keyStorePassword)
                .withKeyStoreType(keyStoreType)
                .withKeyStoreCertificates(keyStoreCertificates)
                .withKubernetesLocations(keyStoreKubernetes)
                .withSecretLocation(keyStoreSecret)
                .withWatch(keyStoreWatch)
                .withDefaultAlias(keyStoreDefaultAlias)
                .build();

            final VertxKeyStoreManager keyStoreManager = new VertxKeyStoreManager(sni);
            final KeyStoreLoader keyStoreLoader = keyStoreLoaderManager.create(keyStoreLoaderOptions);
            keyStoreManager.registerLoader(keyStoreLoader);
            options.setKeyCertOptions(keyStoreManager.getKeyCertOptions());
        }

        // Customizable configuration
        options.setIdleTimeout(idleTimeout);
        options.setTcpKeepAlive(tcpKeepAlive);
    }
}
