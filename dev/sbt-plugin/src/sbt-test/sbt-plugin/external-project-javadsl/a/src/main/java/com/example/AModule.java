/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class AModule extends AbstractModule implements ServiceGuiceSupport {
    @Override
    public void configure() {
        bindService(A.class, AImpl.class);
    }
}
