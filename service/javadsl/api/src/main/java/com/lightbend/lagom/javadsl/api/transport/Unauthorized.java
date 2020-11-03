/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.api.transport;

import com.lightbend.lagom.javadsl.api.deser.ExceptionMessage;

/** Exception thrown when a service call is unauthorized. */
public class Unauthorized extends TransportException {

  private static final long serialVersionUID = 1L;

  public static final TransportErrorCode ERROR_CODE = TransportErrorCode.Unauthorized;

  public Unauthorized(String message) {
    super(ERROR_CODE, message);
  }

  public Unauthorized(Throwable cause) {
    super(ERROR_CODE, cause);
  }

  public Unauthorized(TransportErrorCode errorCode, ExceptionMessage exceptionMessage) {
    super(errorCode, exceptionMessage);
  }
}
