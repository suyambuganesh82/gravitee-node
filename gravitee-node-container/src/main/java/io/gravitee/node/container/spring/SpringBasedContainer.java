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
package io.gravitee.node.container.spring;

import io.gravitee.kubernetes.client.spring.KubernetesClientConfiguration;
import io.gravitee.node.api.Node;
import io.gravitee.node.cache.spring.NodeCacheConfiguration;
import io.gravitee.node.cluster.spring.NodeClusterConfiguration;
import io.gravitee.node.container.AbstractContainer;
import io.gravitee.node.container.spring.env.EnvironmentConfiguration;
import io.gravitee.node.container.spring.env.PropertiesConfiguration;
import io.gravitee.node.management.http.spring.ManagementConfiguration;
import io.gravitee.node.monitoring.spring.NodeMonitoringConfiguration;
import io.gravitee.node.plugins.service.spring.ServiceConfiguration;
import io.gravitee.node.reporter.spring.ReporterConfiguration;
import io.gravitee.node.secrets.plugins.spring.SecretProviderBeanFactory;
import io.gravitee.node.secrets.service.spring.SecretServiceBeanFactory;
import io.gravitee.plugin.core.spring.PluginConfiguration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public abstract class SpringBasedContainer extends AbstractContainer {

    private AnnotationConfigApplicationContext ctx;

    protected SpringBasedContainer() {
        super();
    }

    @Override
    protected void initialize() {
        super.initialize();

        this.initializeContext();
    }

    protected void initializeContext() {
        ctx = new AnnotationConfigApplicationContext();

        List<Class<?>> classes = annotatedClasses();
        classes.forEach(ctx::register);

        // Finally refresh the context
        ctx.refresh();
    }

    protected List<Class<?>> annotatedClasses() {
        List<Class<?>> classes = new ArrayList<>();

        classes.add(EnvironmentConfiguration.class);
        classes.add(PropertiesConfiguration.class);

        classes.add(PluginConfiguration.class);
        classes.add(ServiceConfiguration.class);

        classes.add(ManagementConfiguration.class);
        classes.add(ReporterConfiguration.class);

        classes.add(NodeContainerConfiguration.class);
        classes.add(SecretProviderBeanFactory.class);
        classes.add(SecretServiceBeanFactory.class);
        classes.add(NodeClusterConfiguration.class);
        classes.add(NodeCacheConfiguration.class);
        classes.add(NodeMonitoringConfiguration.class);
        classes.add(KubernetesClientConfiguration.class);

        return classes;
    }

    @Override
    protected void doStop() throws Exception {
        if (!stopped) {
            if (log.isInfoEnabled()) {
                log.info("Shutting-down {}...", name());
            }
            try {
                node().stop();
            } catch (Exception ex) {
                LoggerFactory.getLogger(this.getClass()).error("Unexpected error", ex);
            } finally {
                ctx.close();
                stopped = true;
            }
        }
    }

    @Override
    public Node node() {
        // Get a reference to the node
        return ctx.getBean(Node.class);
    }

    public ApplicationContext applicationContext() {
        return ctx;
    }
}
