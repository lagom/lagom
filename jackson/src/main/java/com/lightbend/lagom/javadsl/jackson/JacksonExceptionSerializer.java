/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.jackson;

import akka.util.ByteString$;
import akka.util.ByteStringBuilder;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.lightbend.lagom.javadsl.api.deser.ExceptionMessage;
import com.lightbend.lagom.javadsl.api.deser.ExceptionSerializer;
import com.lightbend.lagom.javadsl.api.deser.RawExceptionMessage;
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.lightbend.lagom.javadsl.api.transport.TransportException;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Serializes errors to JSON.
 */
public class JacksonExceptionSerializer implements ExceptionSerializer {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
            .registerModule(new Jdk8Module())
            .registerModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES));

    @Override
    public RawExceptionMessage serialize(Throwable exception, Collection<MessageProtocol> accept) {
        Throwable unwrapped = unwrap(exception);
        TransportErrorCode errorCode;
        ExceptionMessage message;
        if (unwrapped instanceof TransportException) {
            TransportException transportException = (TransportException) unwrapped;
            errorCode = transportException.errorCode();
            message = transportException.exceptionMessage();
        } else {
            errorCode = TransportErrorCode.InternalServerError;
            // By default, don't give out information about generic exceptions.
            message = new ExceptionMessage("Exception", "");
        }

        ByteStringBuilder builder = ByteString$.MODULE$.newBuilder();
        try {
            objectMapper.writeValue(builder.asOutputStream(), message);
        } catch (Exception e) {
            // Ignore, simply send on message
            // todo: Log error
        }
        return new RawExceptionMessage(errorCode,
                new MessageProtocol(Optional.of("application/json"), Optional.of("utf-8"), Optional.empty()),
                builder.result());
    }

    /**
     * Unwrap the given exception from known exception wrapper types.
     */
    private Throwable unwrap(Throwable exception) {
        if (exception.getCause() != null) {
            if (exception instanceof ExecutionException || exception instanceof InvocationTargetException || exception instanceof CompletionException) {
                return unwrap(exception.getCause());
            }
        }
        return exception;
    }

    @Override
    public Throwable deserialize(RawExceptionMessage message) {
        ExceptionMessage exceptionMessage;
        try {
            exceptionMessage = objectMapper.readValue(message.message().iterator().asInputStream(), ExceptionMessage.class);
        } catch (Exception e) {
            exceptionMessage = new ExceptionMessage("UndeserializableException", message.message().utf8String());
        }
        return TransportException.fromCodeAndMessage(message.errorCode(), exceptionMessage);
    }
}
