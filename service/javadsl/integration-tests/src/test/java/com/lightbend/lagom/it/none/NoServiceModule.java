/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it.none;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.api.ServiceInfo;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class NoServiceModule extends AbstractModule implements ServiceGuiceSupport {
    @Override
    protected void configure() {
        bindServiceInfo(ServiceInfo.of("brokerConsumer"));
        bindClient(PublisherService.class);
        bind(BrokerConsumer.class).asEagerSingleton();
    }
}
