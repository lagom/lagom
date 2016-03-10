/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.it.mocks;

public class MockId {

    private final String part1;
    private final int part2;

    public MockId(String part1, int part2) {
        this.part1 = part1;
        this.part2 = part2;
    }

    public String part1() {
        return part1;
    }

    public int part2() {
        return part2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MockId mockId = (MockId) o;

        if (part2 != mockId.part2) return false;
        return part1.equals(mockId.part1);

    }

    @Override
    public int hashCode() {
        int result = part1.hashCode();
        result = 31 * result + part2;
        return result;
    }

    @Override
    public String toString() {
        return "MockId{" +
                "part1='" + part1 + '\'' +
                ", part2=" + part2 +
                '}';
    }
}
