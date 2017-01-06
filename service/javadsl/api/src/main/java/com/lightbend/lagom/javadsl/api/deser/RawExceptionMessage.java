/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.deser;

import akka.util.ByteString;
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;

import java.util.Base64;

/**
 * A serialized exception message.
 *
 * A serialized exception message consists of a transport error code, a protocol, and a message body. All, some or none
 * of these details may be sent over the wire when the error is sent, depending on what the underlying protocol
 * supports.
 *
 * Some protocols have a maximum limit on the amount of data that can be sent with an error message, eg for WebSockets,
 * the WebSocket close frame can have a maximum payload of 125 bytes.  While it's up to the transport implementation
 * itself to enforce this limit and gracefully handle when the message exceeds this, exception serializers should be
 * aware of this when producing exception messages.
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

    /**
     * The error code.
     *
     * This will be sent as an HTTP status code, or WebSocket close code.
     *
     * @return The error code.
     */
    public TransportErrorCode errorCode() {
        return errorCode;
    }

    /**
     * The protocol.
     *
     * @return The protocol.
     */
    public MessageProtocol protocol() {
        return protocol;
    }

    /**
     * The message.
     *
     * @return The message.
     */
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RawExceptionMessage that = (RawExceptionMessage) o;

        if (!errorCode.equals(that.errorCode)) return false;
        if (!protocol.equals(that.protocol)) return false;
        return message.equals(that.message);

    }

    @Override
    public int hashCode() {
        int result = errorCode.hashCode();
        result = 31 * result + protocol.hashCode();
        result = 31 * result + message.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "RawExceptionMessage{" +
                "errorCode=" + errorCode +
                ", protocol=" + protocol +
                ", message=" + message +
                '}';
    }
}
