/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.api.transport;

import com.lightbend.lagom.javadsl.api.deser.ExceptionMessage;

/** Exception thrown when the user has sent too many requests in a given amount of time. */
public class TooManyRequests extends TransportException {

  private static final long serialVersionUID = 1L;

  public static final TransportErrorCode ERROR_CODE = TransportErrorCode.TooManyRequests;

  public TooManyRequests(String message) {
    super(ERROR_CODE, message);
  }

  public TooManyRequests(Throwable cause) {
    super(ERROR_CODE, cause);
  }

  public TooManyRequests(TransportErrorCode errorCode, ExceptionMessage exceptionMessage) {
    super(errorCode, exceptionMessage);
  }
}
