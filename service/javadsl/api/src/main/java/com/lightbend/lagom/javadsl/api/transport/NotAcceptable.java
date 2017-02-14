/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.transport;

import com.lightbend.lagom.javadsl.api.deser.ExceptionMessage;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Thrown when a protocol requested by the client cannot be negotiated.
 */
public class NotAcceptable extends TransportException {

    private static final long serialVersionUID = 1L;

    public static final TransportErrorCode ERROR_CODE = TransportErrorCode.NotAcceptable;

    public NotAcceptable(Collection<MessageProtocol> requested, MessageProtocol supported) {
        super(ERROR_CODE, "The requested protocol type/versions: (" +
                requested.stream().map(MessageProtocol::toString).collect(Collectors.joining(", ")) +
                ") could not be satisfied by the server, the default that the server uses is: " + supported);
    }

    public NotAcceptable(TransportErrorCode errorCode, ExceptionMessage exceptionMessage) {
        super(errorCode, exceptionMessage);
    }
}
