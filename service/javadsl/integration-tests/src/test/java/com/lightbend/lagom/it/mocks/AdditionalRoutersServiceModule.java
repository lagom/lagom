/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.it.mocks;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.it.*;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class AdditionalRoutersServiceModule  extends AbstractModule implements ServiceGuiceSupport {
    @Override
    protected void configure() {
        bindService(
            AdditionalRoutersService.class, AdditionalRoutersServiceImpl.class,

            // bind a router using an instance and prefix it using the bind dsl
            additionalRouter(PingRouter.newInstanceJava()).withPrefix("/ping"),

            // bind a prefixed router using an instance
            additionalRouter(PongRouter.newInstanceJava()),

            // bind a router using a class (DI case) and prefix it using bind dsl
            additionalRouter(HelloRouter.class).withPrefix("/hello"),

            // bind a prefixed router using a class (DI case)
            // (that router is already prefixed with /hello-prefixed)
            additionalRouter(PrefixedHelloRouter.class)
        );
    }

}
