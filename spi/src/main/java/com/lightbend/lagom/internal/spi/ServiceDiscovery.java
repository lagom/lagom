/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.spi;

import java.util.List;

/**
 * This class can be implemented by a Play ApplicationLoader to allow tooling to detect services and their ACLs.
 */
public interface ServiceDiscovery {

    List<ServiceDescription> discoverServices(ClassLoader classLoader);

}
