/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit.services;

public class BetaEvent {

    private final int code;

    public BetaEvent(int code){
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BetaEvent aEvent = (BetaEvent) o;

        return code == aEvent.code;
    }

    @Override
    public int hashCode() {
        return code;
    }

    @Override
    public String
    toString() {
        return "BetaEvent{" +
                "code=" + code +
                '}';
    }
}
