/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.it.mocks;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.it.AdNauseamRouter;
import com.lightbend.lagom.it.GreeterRouter;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class AdditionalRoutersServiceModule  extends AbstractModule implements ServiceGuiceSupport {
    @Override
    protected void configure() {
        serverFor(AdditionalRoutersService.class, AdditionalRoutersServiceImpl.class)
            .withAdditionalRouters(
                bindRouter(AdNauseamRouter.newInstanceJava("ping")).withPrefix("/ping"),
                bindRouter(AdNauseamRouter.newInstanceJava("pong")).withPrefix("/pong"),
                bindRouter(GreeterRouter.class).withPrefix("/echo")
            )
            .bind();
    }

}
