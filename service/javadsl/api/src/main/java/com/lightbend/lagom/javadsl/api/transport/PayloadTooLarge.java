/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.transport;

import com.lightbend.lagom.javadsl.api.deser.ExceptionMessage;

/**
 * Thrown when the payload is too large.
 */
public class PayloadTooLarge extends TransportException {
    public static final TransportErrorCode ERROR_CODE = TransportErrorCode.PayloadTooLarge;

    public PayloadTooLarge(String message) {
        super(ERROR_CODE, message);
    }

    public PayloadTooLarge(Throwable cause) {
        super(ERROR_CODE, cause);
    }

    public PayloadTooLarge(TransportErrorCode errorCode, ExceptionMessage exceptionMessage) {
        super(errorCode, exceptionMessage);
    }
}
