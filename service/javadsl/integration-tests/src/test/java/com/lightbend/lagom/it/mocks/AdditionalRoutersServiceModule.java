/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it.mocks;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.internal.javadsl.server.AdditionalRouters;
import com.lightbend.lagom.it.AdNauseamRouter;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import play.routing.Router;

import javax.inject.Provider;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AdditionalRoutersServiceModule  extends AbstractModule implements ServiceGuiceSupport {
    @Override
    protected void configure() {
        serverFor(AdditionalRoutersService.class, AdditionalRoutersServiceImpl.class)
            .withAdditionalRouters(PingPongProvider.class)
            .bind();
    }


    static class PingPongProvider implements Provider<AdditionalRouters> {
        @Override
        public AdditionalRouters get() {
            return new AdditionalRouters() {
                @Override
                public List<Router> getRouters() {
                    return Arrays.asList(
                        AdNauseamRouter.newInstanceJava("ping").withPrefix("/ping"),
                        AdNauseamRouter.newInstanceJava("pong").withPrefix("/pong")
                    );
                }
            };
        }
    }

}
