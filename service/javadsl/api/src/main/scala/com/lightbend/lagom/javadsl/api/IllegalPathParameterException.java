/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api;

public class IllegalPathParameterException  extends IllegalArgumentException{

    public IllegalPathParameterException(String message, Throwable cause) {
        super(message, cause);
    }
    
}
