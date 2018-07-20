/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.api.transport;

import com.lightbend.lagom.javadsl.api.deser.ExceptionMessage;

/**
 * Exception thrown when the resource was not found.
 */
public class NotFound extends TransportException {

    private static final long serialVersionUID = 1L;

    public static final TransportErrorCode ERROR_CODE = TransportErrorCode.NotFound;

    public NotFound(String message) {
        super(ERROR_CODE, message);
    }

    public NotFound(Throwable cause) {
        super(ERROR_CODE, cause);
    }

    public NotFound(TransportErrorCode errorCode, ExceptionMessage exceptionMessage) {
        super(errorCode, exceptionMessage);
    }
}
