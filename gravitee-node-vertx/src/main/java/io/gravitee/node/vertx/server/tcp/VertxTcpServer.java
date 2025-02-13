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
package io.gravitee.node.vertx.server.tcp;

import io.gravitee.node.vertx.server.VertxServer;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.net.NetServer;
import java.util.Collections;
import java.util.List;

/**
 * @author Benoit BORDIGONI (benoit.bordigoni at graviteesource.com)
 * @author GraviteeSource Team
 */
public class VertxTcpServer extends VertxServer<NetServer, VertxTcpServerOptions> {

    public static final String KIND = "tcp";

    public VertxTcpServer(String id, Vertx vertx, VertxTcpServerOptions options) {
        super(id, vertx, options);
    }

    @Override
    public String type() {
        return KIND;
    }

    @Override
    public NetServer newInstance() {
        final NetServer netServer = vertx.createNetServer(options.createNetServerOptions());
        delegates.add(netServer);
        return netServer;
    }

    @Override
    public List<NetServer> instances() {
        return Collections.unmodifiableList(delegates);
    }

    @Override
    public VertxTcpServerOptions options() {
        return options;
    }
}
