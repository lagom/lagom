/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.spi;

import java.util.List;
import java.util.Optional;

/**
 * This class can be implemented by a Play ApplicationLoader to allow tooling to detect services and their ACLs.
 */
public interface ServiceDiscovery {
    /**
     * @deprecated support for multiple locatable ServiceDescriptors per Lagom service was
     *             removed in 1.3.1. Use {@link ServiceDiscovery@discoverService} instead
     */
    @Deprecated
    List<ServiceDescription> discoverServices(ClassLoader classLoader);

    Optional<ServiceDescription> discoverService(ClassLoader classLoader);
}
