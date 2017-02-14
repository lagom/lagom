/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.sbt.server;

import java.net.InetSocketAddress;

import play.core.server.ServerWithStop;

/** 
 * A server that can reload the running application.
 */
public abstract class ReloadableServer implements ServerWithStop {
    private final ServerWithStop server;

    public ReloadableServer(ServerWithStop server) {
        this.server = server;
    }

    /** Executes application's reloading.*/
    public abstract void reload();

    public void stop() { server.stop(); }

    public InetSocketAddress mainAddress() { return server.mainAddress(); }
}
