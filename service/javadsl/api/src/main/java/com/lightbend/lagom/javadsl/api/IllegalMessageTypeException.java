/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api;

/** Exception thrown when a message type is illegal. */
public class IllegalMessageTypeException extends IllegalArgumentException {
  public IllegalMessageTypeException(String message, Throwable cause) {
    super(message, cause);
  }
}
