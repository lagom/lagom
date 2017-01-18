/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit.services;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class Module extends AbstractModule implements ServiceGuiceSupport {
    @Override
    protected void configure() {
        bindServices(
                serviceBinding(CharlieDownstreamService.class, CharlieDownstreamService.Impl.class),
                serviceBinding(DeltaDownstreamService.class, DeltaDownstreamService.Impl.class),
                serviceBinding(FoxtrotDownstreamService.class, FoxtrotDownstreamService.Impl.class)
        );
    }
}
