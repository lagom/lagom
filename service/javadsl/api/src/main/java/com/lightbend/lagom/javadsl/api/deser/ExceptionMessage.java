/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.api.deser;

import java.io.Serializable;
import java.util.Objects;

/**
 * A high level exception message.
 *
 * <p>This is used by the default exception serializer to serialize exceptions into messages.
 */
public class ExceptionMessage implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String name;
  private final String detail;

  public ExceptionMessage(String name, String detail) {
    this.name = name;
    this.detail = detail;
  }

  public String name() {
    return name;
  }

  public String detail() {
    return detail;
  }

  @Override
  public String toString() {
    return "ExceptionMessage{" + "name='" + name + '\'' + ", detail='" + detail + '\'' + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ExceptionMessage that = (ExceptionMessage) o;
    return Objects.equals(name, that.name) && Objects.equals(detail, that.detail);
  }

  @Override
  public int hashCode() {

    return Objects.hash(name, detail);
  }
}
