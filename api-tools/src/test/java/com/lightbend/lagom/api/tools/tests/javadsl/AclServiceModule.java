/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.api.tools.tests.javadsl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class AclServiceModule extends AbstractModule implements ServiceGuiceSupport {
    @Override
    protected void configure() {
        bindService(AclService.class, AclServiceImpl.class);
    }
}
