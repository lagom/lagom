/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.deser;

import akka.util.ByteString;
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;

import java.util.Base64;

/**
 * A serialized exception message.
 */
public class RawExceptionMessage {

    private final TransportErrorCode errorCode;
    private final MessageProtocol protocol;
    private final ByteString message;

    public RawExceptionMessage(TransportErrorCode errorCode, MessageProtocol protocol, ByteString message) {
        this.errorCode = errorCode;
        this.protocol = protocol;
        this.message = message;
    }

    public TransportErrorCode errorCode() {
        return errorCode;
    }

    public MessageProtocol protocol() {
        return protocol;
    }

    public ByteString message() {
        return message;
    }

    /**
     * Get the message as text.
     *
     * If this is a binary message (that is, the message protocol does not define a charset), encodes it using Base64.
     *
     * @return The message as text.
     */
    public String messageAsText() {
        if (protocol.charset().isPresent()) {
            return message.decodeString(protocol.charset().get());
        } else {
            return Base64.getEncoder().encodeToString(message.toArray());
        }
    }
}
