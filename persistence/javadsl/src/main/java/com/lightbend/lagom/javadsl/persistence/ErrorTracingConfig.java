/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence;

public class ErrorTracingConfig {
    public final boolean logClusterStateOnTimeout;
    public final boolean logCommandsPayloadOnTimeout;


    public ErrorTracingConfig(boolean logClusterStateOnTimeout, boolean logCommandsOnTimeout) {
        this.logClusterStateOnTimeout = logClusterStateOnTimeout;
        this.logCommandsPayloadOnTimeout = logCommandsOnTimeout;
    }
}
