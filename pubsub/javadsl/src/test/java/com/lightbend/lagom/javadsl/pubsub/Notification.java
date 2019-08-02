/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.pubsub;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.lightbend.lagom.serialization.Jsonable;

public class Notification implements Jsonable {

  private static final long serialVersionUID = 1L;

  private final String msg;

  @JsonCreator
  public Notification(String msg) {
    this.msg = msg;
  }

  public String getMsg() {
    return msg;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((msg == null) ? 0 : msg.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    Notification other = (Notification) obj;
    if (msg == null) {
      if (other.msg != null) return false;
    } else if (!msg.equals(other.msg)) return false;
    return true;
  }

  @Override
  public String toString() {
    return "Notification [msg=" + msg + "]";
  }
}
