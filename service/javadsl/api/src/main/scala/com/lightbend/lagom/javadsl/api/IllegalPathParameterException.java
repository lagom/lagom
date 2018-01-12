/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api;

public class IllegalPathParameterException  extends IllegalArgumentException{

    public IllegalPathParameterException(String message, Throwable cause) {
        super(message, cause);
    }
    
}
