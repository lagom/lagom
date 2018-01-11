/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit.services;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class PublishModule extends AbstractModule implements ServiceGuiceSupport {
    @Override
    protected void configure() {
        bindService(PublishService.class, PublishServiceImpl.class);
    }
}
