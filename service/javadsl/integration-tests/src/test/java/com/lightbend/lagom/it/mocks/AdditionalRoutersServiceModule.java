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
import java.util.Collections;
import java.util.List;

public class AdditionalRoutersServiceModule  extends AbstractModule implements ServiceGuiceSupport {
    @Override
    protected void configure() {
        serverFor(AdditionalRoutersService.class, AdditionalRoutersServiceImpl.class)
            .withAdditionalRouters(PingProvider.class)
            .withAdditionalRouters(new PongProvider("pong"))
            .bind();
    }


    static class PingProvider implements Provider<AdditionalRouters> {
        @Override
        public AdditionalRouters get() {
            return new AdditionalRouters() {
                @Override
                public List<Router> getRouters() {
                    return Collections.singletonList(
                        AdNauseamRouter.newInstanceJava("ping").withPrefix("/ping")
                    );
                }
            };
        }
    }

    static class PongProvider implements Provider<AdditionalRouters> {
        final private String msg;

        PongProvider(String msg) {
            this.msg = msg;
        }

        @Override
        public AdditionalRouters get() {
            return new AdditionalRouters() {
                @Override
                public List<Router> getRouters() {
                    return Collections.singletonList(
                        AdNauseamRouter.newInstanceJava(msg).withPrefix("/" + msg)
                    );
                }
            };
        }
    }
}
