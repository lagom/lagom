/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it.mocks;

public class MockRequestEntity {

    private final String field1;
    private final int field2;

    public MockRequestEntity(String field1, int field2) {
        this.field1 = field1;
        this.field2 = field2;
    }

    public String field1() {
        return field1;
    }

    public int field2() {
        return field2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MockRequestEntity that = (MockRequestEntity) o;

        if (field2 != that.field2) return false;
        return field1.equals(that.field1);

    }

    @Override
    public int hashCode() {
        int result = field1.hashCode();
        result = 31 * result + field2;
        return result;
    }

    @Override
    public String toString() {
        return "MockRequestEntity{" +
                "field1='" + field1 + '\'' +
                ", field2=" + field2 +
                '}';
    }
}
