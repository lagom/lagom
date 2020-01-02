/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.testkit.services;

import com.fasterxml.jackson.annotation.JsonCreator;

public class PublishEvent {

  private final int code;

  @JsonCreator
  public PublishEvent(int code) {
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PublishEvent that = (PublishEvent) o;

    return code == that.code;
  }

  @Override
  public int hashCode() {
    return code;
  }

  @Override
  public String toString() {
    return "PublishEvent{" + "code=" + code + '}';
  }
}
