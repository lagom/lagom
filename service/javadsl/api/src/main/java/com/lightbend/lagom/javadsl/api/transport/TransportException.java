/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.transport;

import com.lightbend.lagom.javadsl.api.deser.DeserializationException;
import com.lightbend.lagom.javadsl.api.deser.ExceptionMessage;
import com.lightbend.lagom.javadsl.api.deser.SerializationException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * An exception that can be translated down to a specific error in the transport.
 */
public class TransportException extends RuntimeException {
    private final TransportErrorCode errorCode;
    private final ExceptionMessage exceptionMessage;

    protected TransportException(TransportErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.exceptionMessage = new ExceptionMessage(this.getClass().getSimpleName(), message);
    }

    protected TransportException(TransportErrorCode errorCode, Throwable cause) {
        super(cause.getMessage(), cause);
        this.errorCode = errorCode;
        this.exceptionMessage = new ExceptionMessage(this.getClass().getSimpleName(), cause.getMessage());
    }

    public TransportException(TransportErrorCode errorCode, ExceptionMessage exceptionMessage) {
        super(exceptionMessage.detail());
        this.errorCode = errorCode;
        this.exceptionMessage = exceptionMessage;
    }

    public static TransportException fromCodeAndMessage(TransportErrorCode errorCode, ExceptionMessage exceptionMessage) {
        BiFunction<TransportErrorCode, ExceptionMessage, TransportException> creator = BY_NAME_TRANSPORT_EXCEPTIONS.get(exceptionMessage.name());
        if (creator != null) {
            return creator.apply(errorCode, exceptionMessage);
        } else {
            creator = BY_CODE_TRANSPORT_EXCEPTIONS.get(errorCode);
            if (creator != null) {
                return creator.apply(errorCode, exceptionMessage);
            } else {
                return new TransportException(errorCode, exceptionMessage);
            }
        }
    }

    /**
     * The error code that should be sent to the transport.
     *
     * @return The error code.
     */
    public TransportErrorCode errorCode() {
        return errorCode;
    }

    /**
     * The message that should be sent to the transport.
     *
     * @return The message.
     */
    public ExceptionMessage exceptionMessage() {
        return exceptionMessage;
    }

    private static final Map<String, BiFunction<TransportErrorCode, ExceptionMessage, TransportException>> BY_NAME_TRANSPORT_EXCEPTIONS;
    private static final Map<TransportErrorCode, BiFunction<TransportErrorCode, ExceptionMessage, TransportException>> BY_CODE_TRANSPORT_EXCEPTIONS;

    static {
        Map<String, BiFunction<TransportErrorCode, ExceptionMessage, TransportException>> byName = new HashMap<>();
        byName.put(DeserializationException.class.getSimpleName(), DeserializationException::new);
        byName.put(SerializationException.class.getSimpleName(), SerializationException::new);
        byName.put(UnsupportedMediaType.class.getSimpleName(), UnsupportedMediaType::new);
        byName.put(NotAcceptable.class.getSimpleName(), NotAcceptable::new);
        byName.put(PolicyViolation.class.getSimpleName(), PolicyViolation::new);
        byName.put(NotFound.class.getSimpleName(), NotFound::new);
        byName.put(Forbidden.class.getSimpleName(), Forbidden::new);

        Map<TransportErrorCode, BiFunction<TransportErrorCode, ExceptionMessage, TransportException>> byCode = new HashMap<>();
        byCode.put(DeserializationException.ERROR_CODE, DeserializationException::new);
        byCode.put(UnsupportedMediaType.ERROR_CODE, UnsupportedMediaType::new);
        byCode.put(NotAcceptable.ERROR_CODE, NotAcceptable::new);
        byCode.put(PolicyViolation.ERROR_CODE, PolicyViolation::new);
        byCode.put(Forbidden.ERROR_CODE, Forbidden::new);

        BY_NAME_TRANSPORT_EXCEPTIONS = Collections.unmodifiableMap(byName);
        BY_CODE_TRANSPORT_EXCEPTIONS = Collections.unmodifiableMap(byCode);
    }

    @Override
    public String toString() {
        return super.toString() + " (" + errorCode + ")";
    }
}
