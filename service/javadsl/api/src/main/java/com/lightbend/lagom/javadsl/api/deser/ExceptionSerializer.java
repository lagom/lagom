/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.deser;

import com.lightbend.lagom.internal.javadsl.api.JacksonPlaceholderExceptionSerializer$;
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol;

import java.util.Collection;

/**
 * Handles the serialization and deserialization of exceptions.
 */
public interface ExceptionSerializer {

    /**
     * Serialize the given exception to an exception message.
     *
     * The raw exception message consists of an error code, a message protocol, and a message entity to send across the
     * wire.
     *
     * The exception serializer may attempt to match one of the protocols passed into the accept parameter.
     *
     * @param exception The exception to serialize.
     * @param accept The accepted protocols.
     * @return The raw exception message.
     */
    RawExceptionMessage serialize(Throwable exception, Collection<MessageProtocol> accept);

    /**
     * Deserialize an exception message into an exception.
     *
     * The exception serializer should make a best effort attempt at deserializing the message, but should not expect
     * the message to be in any particular format.  If it cannot deserialize the message, it should return a generic
     * exception, it should not itself throw an exception.
     *
     * @param message The message to deserialize.
     * @return The deserialized exception.
     */
    Throwable deserialize(RawExceptionMessage message);

    /**
     * The default Jackson exception serializer.
     *
     * This is only a placeholder, the framework will provide the actual implementation at runtime.
     */
    ExceptionSerializer JACKSON = JacksonPlaceholderExceptionSerializer$.MODULE$;

    /**
     * The default serializer factory.
     */
    ExceptionSerializer DEFAULT = JACKSON;

}
