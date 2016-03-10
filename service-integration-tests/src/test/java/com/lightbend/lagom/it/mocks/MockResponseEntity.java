/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.it.mocks;

public class MockResponseEntity {

    private final MockId incomingMockId;
    private final MockRequestEntity incomingRequest;

    public MockResponseEntity(MockId incomingMockId, MockRequestEntity incomingRequest) {
        this.incomingMockId = incomingMockId;
        this.incomingRequest = incomingRequest;
    }

    public MockId incomingMockId() {
        return incomingMockId;
    }

    public MockRequestEntity incomingRequest() {
        return incomingRequest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MockResponseEntity that = (MockResponseEntity) o;

        if (!incomingMockId.equals(that.incomingMockId)) return false;
        return incomingRequest.equals(that.incomingRequest);

    }

    @Override
    public int hashCode() {
        int result = incomingMockId.hashCode();
        result = 31 * result + incomingRequest.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "MockResponseEntity{" +
                "incomingMockId=" + incomingMockId +
                ", incomingRequest=" + incomingRequest +
                '}';
    }
}
