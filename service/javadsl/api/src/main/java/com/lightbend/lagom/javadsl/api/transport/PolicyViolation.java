/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.transport;

import com.lightbend.lagom.javadsl.api.deser.ExceptionMessage;

/**
 * Exception thrown when there was a generic client error.
 */
public class PolicyViolation extends TransportException {

    private static final long serialVersionUID = 1L;

    public static final TransportErrorCode ERROR_CODE = TransportErrorCode.PolicyViolation;

    public PolicyViolation(String message) {
        super(ERROR_CODE, message);
    }

    public PolicyViolation(Throwable cause) {
        super(ERROR_CODE, cause);
    }

    public PolicyViolation(TransportErrorCode errorCode, ExceptionMessage exceptionMessage) {
        super(errorCode, exceptionMessage);
    }
}
