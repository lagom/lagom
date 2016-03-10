/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.deser;

import org.pcollections.PSequence;

public class RawIdDescriptor {
    private final PSequence<String> pathParams;
    private final PSequence<String> queryParams;

    public RawIdDescriptor(PSequence<String> pathParams, PSequence<String> queryParams) {
        this.pathParams = pathParams;
        this.queryParams = queryParams;
    }

    public PSequence<String> pathParams() {
        return pathParams;
    }

    public PSequence<String> queryParams() {
        return queryParams;
    }

    public RawIdDescriptor removeNext() {
        if (!pathParams.isEmpty()) {
            return new RawIdDescriptor(pathParams.subList(1, pathParams.size()), queryParams);
        } else if (!queryParams.isEmpty()) {
            return new RawIdDescriptor(pathParams, queryParams.subList(1, queryParams.size()));
        } else {
            throw new IllegalArgumentException("No parameters to remove from descriptor");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RawIdDescriptor that = (RawIdDescriptor) o;

        if (!pathParams.equals(that.pathParams)) return false;
        return queryParams.equals(that.queryParams);

    }

    @Override
    public int hashCode() {
        int result = pathParams.hashCode();
        result = 31 * result + queryParams.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "RawIdDescriptor{" +
                "pathParams=" + pathParams +
                ", queryParams=" + queryParams +
                '}';
    }
}
