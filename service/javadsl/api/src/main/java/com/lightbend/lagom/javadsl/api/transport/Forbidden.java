/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.api.transport;

import com.lightbend.lagom.javadsl.api.deser.ExceptionMessage;

/**
 * Exception thrown when a service call is forbidden.
 */
public class Forbidden extends TransportException {

    private static final long serialVersionUID = 1L;

    public static final TransportErrorCode ERROR_CODE = TransportErrorCode.Forbidden;

    public Forbidden(String message) {
        super(ERROR_CODE, message);
    }

    public Forbidden(Throwable cause) {
        super(ERROR_CODE, cause);
    }

    public Forbidden(TransportErrorCode errorCode, ExceptionMessage exceptionMessage) {
        super(errorCode, exceptionMessage);
    }
}
