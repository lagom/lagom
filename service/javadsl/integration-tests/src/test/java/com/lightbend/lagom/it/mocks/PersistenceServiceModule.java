/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it.mocks;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class PersistenceServiceModule extends AbstractModule implements ServiceGuiceSupport {
    @Override
    protected void configure() {
        bindServices(serviceBinding(PersistenceService.class, PersistenceServiceImpl.class));
    }
}
