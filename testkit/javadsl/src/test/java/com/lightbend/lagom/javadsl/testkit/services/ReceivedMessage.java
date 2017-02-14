/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit.services;

import com.lightbend.lagom.serialization.Jsonable;


public class ReceivedMessage implements Jsonable {

    private static final long serialVersionUID = 1L;

    private String source;
    private int msg;

    public ReceivedMessage(String source, int msg){
        this.source = source;
        this.msg = msg;
    }

    public String getSource() {
        return source;
    }

    public int getMsg() {
        return msg;
    }

    @Override
    public String toString() {
        return "ReceivedMessage{" +
                "source='" + source + '\'' +
                ", msg=" + msg +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReceivedMessage that = (ReceivedMessage) o;

        if (msg != that.msg) return false;
        return source != null ? source.equals(that.source) : that.source == null;
    }

    @Override
    public int hashCode() {
        int result = source != null ? source.hashCode() : 0;
        result = 31 * result + msg;
        return result;
    }
}
