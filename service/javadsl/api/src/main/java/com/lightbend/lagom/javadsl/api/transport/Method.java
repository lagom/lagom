/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.transport;

/**
 * A transport method.
 *
 * If the transport is HTTP, this maps down to an HTTP method.
 */
public final class Method {
    public static final Method GET = new Method("GET");
    public static final Method POST = new Method("POST");
    public static final Method PUT = new Method("PUT");
    public static final Method DELETE = new Method("DELETE");
    public static final Method HEAD = new Method("HEAD");
    public static final Method OPTIONS = new Method("OPTIONS");
    public static final Method PATCH = new Method("PATCH");

    private final String name;

    public Method(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Method method = (Method) o;

        return name.equals(method.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
