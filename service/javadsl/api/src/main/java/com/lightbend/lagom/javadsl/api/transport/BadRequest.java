/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.transport;

import com.lightbend.lagom.javadsl.api.deser.ExceptionMessage;

/**
 * Thrown when the request is bad.
 */
public class BadRequest extends TransportException {

    private static final long serialVersionUID = 1L;

    public static final TransportErrorCode ERROR_CODE = TransportErrorCode.BadRequest;

    public BadRequest(String message) {
        super(ERROR_CODE, message);
    }

    public BadRequest(Throwable cause) {
        super(ERROR_CODE, cause);
    }

    public BadRequest(TransportErrorCode errorCode, ExceptionMessage exceptionMessage) {
        super(errorCode, exceptionMessage);
    }
}
