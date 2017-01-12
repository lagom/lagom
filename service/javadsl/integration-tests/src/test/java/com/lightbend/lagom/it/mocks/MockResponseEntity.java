/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it.mocks;

public class MockResponseEntity {

    private final long incomingId;
    private final MockRequestEntity incomingRequest;

    public MockResponseEntity(long incomingId, MockRequestEntity incomingRequest) {
        this.incomingId = incomingId;
        this.incomingRequest = incomingRequest;
    }

    public long incomingId() {
        return incomingId;
    }

    public MockRequestEntity incomingRequest() {
        return incomingRequest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MockResponseEntity)) return false;

        MockResponseEntity that = (MockResponseEntity) o;

        if (incomingId != that.incomingId) return false;
        return incomingRequest.equals(that.incomingRequest);

    }

    @Override
    public int hashCode() {
        int result = (int) (incomingId ^ (incomingId >>> 32));
        result = 31 * result + incomingRequest.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "MockResponseEntity{" +
                "incomingId=" + incomingId +
                ", incomingRequest=" + incomingRequest +
                '}';
    }
}
