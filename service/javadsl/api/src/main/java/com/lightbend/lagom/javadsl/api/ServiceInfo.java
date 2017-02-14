/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api;

/**
 * Information for this service.
 */
public final class ServiceInfo {

    private final String serviceName;

    public ServiceInfo(String serviceName) {
        this.serviceName = serviceName;
    }

    public String serviceName() {
        return serviceName;
    }
}
