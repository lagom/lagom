/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it.mocks;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class MockServiceModule extends AbstractModule implements ServiceGuiceSupport {
    @Override
    protected void configure() {
        bindService(MockService.class, MockServiceImpl.class);
    }
}
