/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.transport;

import com.lightbend.lagom.javadsl.api.deser.ExceptionMessage;

/**
 * Exception thrown when a message can't be deserialized because its media type is not known.
 */
public class UnsupportedMediaType extends TransportException {
    public static final TransportErrorCode ERROR_CODE = TransportErrorCode.UnsupportedMediaType;

    public UnsupportedMediaType(MessageProtocol received, MessageProtocol supported) {
        super(ERROR_CODE, "Could not negotiate a deserializer for type " + received + ", the default media type supported is " + supported);
    }

    public UnsupportedMediaType(TransportErrorCode errorCode, ExceptionMessage exceptionMessage) {
        super(errorCode, exceptionMessage);
    }
}
