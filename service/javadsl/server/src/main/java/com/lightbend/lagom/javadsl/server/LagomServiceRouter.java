/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.server;

import play.api.routing.Router;

/**
 * A Lagom service router.
 *
 * This interface doesn't add anything, except that it makes the router created by the LagomServer
 * strongly typed. This allows it to be dependency injected by type, making it simple to use it
 * in combination with the Play routes file.
 *
 * For example, if using a custom router, the Lagom router could be routed to from the routes file
 * like this:
 *
 * <pre>
 * -&gt;   /     com.lightbend.lagom.javadsl.server.LagomServiceRouter
 * </pre>
 */
public interface LagomServiceRouter extends Router {
}
