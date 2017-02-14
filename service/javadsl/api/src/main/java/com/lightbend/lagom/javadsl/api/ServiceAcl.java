/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api;

import com.lightbend.lagom.javadsl.api.transport.Method;

import java.util.Optional;

public final class ServiceAcl {

    public static ServiceAcl path(String pathRegex) {
        return new ServiceAcl(Optional.empty(), Optional.of(pathRegex));
    }

    public static ServiceAcl methodAndPath(Method method, String pathRegex) {
        return new ServiceAcl(Optional.of(method), Optional.of(pathRegex));
    }
    
    private final Optional<Method> method;
    private final Optional<String> pathRegex;

    public ServiceAcl(Optional<Method> method, Optional<String> pathRegex) {
        this.method = method;
        this.pathRegex = pathRegex;
    }

    public Optional<Method> method() {
        return method;
    }

    public Optional<String> pathRegex() {
        return pathRegex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceAcl)) return false;

        ServiceAcl that = (ServiceAcl) o;

        if (!method.equals(that.method)) return false;
        return pathRegex.equals(that.pathRegex);

    }

    @Override
    public int hashCode() {
        int result = method.hashCode();
        result = 31 * result + pathRegex.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ServiceAcl{" +
                "method=" + method +
                ", pathRegex=" + pathRegex +
                '}';
    }
}
