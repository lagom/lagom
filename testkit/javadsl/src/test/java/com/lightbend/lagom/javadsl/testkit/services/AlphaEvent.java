/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit.services;

public class AlphaEvent {

    private final int code;

    public AlphaEvent(int code){
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AlphaEvent alphaEvent = (AlphaEvent) o;

        return code == alphaEvent.code;
    }

    @Override
    public int hashCode() {
        return code;
    }

    @Override
    public String
    toString() {
        return "AlphaEvent{" +
                "code=" + code +
                '}';
    }
}
