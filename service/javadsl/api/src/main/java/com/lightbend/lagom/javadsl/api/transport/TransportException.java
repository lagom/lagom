/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.api.transport;

import com.lightbend.lagom.javadsl.api.deser.DeserializationException;
import com.lightbend.lagom.javadsl.api.deser.ExceptionMessage;
import com.lightbend.lagom.javadsl.api.deser.SerializationException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/** An exception that can be translated down to a specific error in the transport. */
public class TransportException extends RuntimeException {

  private static final long serialVersionUID = 1L;

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
    this.exceptionMessage =
        new ExceptionMessage(this.getClass().getSimpleName(), cause.getMessage());
  }

  public TransportException(TransportErrorCode errorCode, ExceptionMessage exceptionMessage) {
    super(exceptionMessage.detail());
    this.errorCode = errorCode;
    this.exceptionMessage = exceptionMessage;
  }

  public TransportException(TransportErrorCode errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
    this.exceptionMessage = new ExceptionMessage(this.getClass().getSimpleName(), message);
  }

  public static TransportException fromCodeAndMessage(
      TransportErrorCode errorCode, ExceptionMessage exceptionMessage) {
    BiFunction<TransportErrorCode, ExceptionMessage, TransportException> creator =
        BY_NAME_TRANSPORT_EXCEPTIONS.get(exceptionMessage.name());
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

  private static final Map<
          String, BiFunction<TransportErrorCode, ExceptionMessage, TransportException>>
      BY_NAME_TRANSPORT_EXCEPTIONS;
  private static final Map<
          TransportErrorCode, BiFunction<TransportErrorCode, ExceptionMessage, TransportException>>
      BY_CODE_TRANSPORT_EXCEPTIONS;

  static {
    // this map keeps a more strict relation between exception message and exception instances.
    // Some exceptions reuse the same status code on certain transports so deserialization should
    // try to reconstruct the exception by name first and fallback to reconstructing by code.
    Map<String, BiFunction<TransportErrorCode, ExceptionMessage, TransportException>> byName =
        new HashMap<>();
    byName.put(DeserializationException.class.getSimpleName(), DeserializationException::new);
    byName.put(BadRequest.class.getSimpleName(), BadRequest::new);
    byName.put(Unauthorized.class.getSimpleName(), Unauthorized::new);
    byName.put(Forbidden.class.getSimpleName(), Forbidden::new);
    byName.put(PolicyViolation.class.getSimpleName(), PolicyViolation::new);
    byName.put(NotFound.class.getSimpleName(), NotFound::new);
    byName.put(NotAcceptable.class.getSimpleName(), NotAcceptable::new);
    byName.put(PayloadTooLarge.class.getSimpleName(), PayloadTooLarge::new);
    byName.put(SerializationException.class.getSimpleName(), SerializationException::new);
    byName.put(UnsupportedMediaType.class.getSimpleName(), UnsupportedMediaType::new);
    byName.put(TooManyRequests.class.getSimpleName(), TooManyRequests::new);

    Map<TransportErrorCode, BiFunction<TransportErrorCode, ExceptionMessage, TransportException>>
        byCode = new HashMap<>();
    byCode.put(DeserializationException.ERROR_CODE, DeserializationException::new);
    byCode.put(Forbidden.ERROR_CODE, Forbidden::new);
    byCode.put(Unauthorized.ERROR_CODE, Unauthorized::new);
    byCode.put(PolicyViolation.ERROR_CODE, PolicyViolation::new);
    byCode.put(NotAcceptable.ERROR_CODE, NotAcceptable::new);
    byCode.put(PayloadTooLarge.ERROR_CODE, PayloadTooLarge::new);
    byCode.put(UnsupportedMediaType.ERROR_CODE, UnsupportedMediaType::new);
    byCode.put(TooManyRequests.ERROR_CODE, TooManyRequests::new);

    BY_NAME_TRANSPORT_EXCEPTIONS = Collections.unmodifiableMap(byName);
    BY_CODE_TRANSPORT_EXCEPTIONS = Collections.unmodifiableMap(byCode);
  }

  @Override
  public String toString() {
    return super.toString() + " (" + errorCode + ")";
  }
}
