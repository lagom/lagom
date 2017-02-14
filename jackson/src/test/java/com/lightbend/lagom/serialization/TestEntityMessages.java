/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.serialization;

import com.fasterxml.jackson.annotation.JsonCreator;

public class TestEntityMessages {
  public static interface Cmd extends Jsonable {
  }

  public static class Get implements Cmd {

    private static final long serialVersionUID = 1L;
    private static final Get INSTANCE = new Get();

    @JsonCreator
    public static Get instance() {
      return Get.INSTANCE;
    }

    private Get() {
    }
  }

  public static class Add implements Cmd {

    private static final long serialVersionUID = 1L;

    private final String element;
    private final int times;

    public static Add of(String element) {
      return new Add(element, 1);
    }

    @JsonCreator
    public Add(String element, int times) {
      this.element = element;
      this.times = times;
    }

    public String getElement() {
      return element;
    }

    public int getTimes() {
      return times;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((element == null) ? 0 : element.hashCode());
      result = prime * result + times;
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      Add other = (Add) obj;
      if (element == null) {
        if (other.element != null)
          return false;
      } else if (!element.equals(other.element))
        return false;
      if (times != other.times)
        return false;
      return true;
    }

    @Override
    public String toString() {
      return "Add [element=" + element + ", times=" + times + "]";
    }

  }

  public enum Mode {
    PREPEND, APPEND
  }

  public static class ChangeMode implements Cmd {

    private static final long serialVersionUID = 1L;

    private final Mode mode;

    @JsonCreator
    public ChangeMode(Mode mode) {
      this.mode = mode;
    }

    public Mode getMode() {
      return mode;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((mode == null) ? 0 : mode.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      ChangeMode other = (ChangeMode) obj;
      if (mode != other.mode)
        return false;
      return true;
    }

    @Override
    public String toString() {
      return "ChangeMode [mode=" + mode + "]";
    }

  }

  public static class UndefinedCmd implements Cmd {

    private static final long serialVersionUID = 1L;

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      else
        return (getClass() == obj.getClass());
    }

    @Override
    public String toString() {
      return "UndefinedCmd";
    }
  }

  public static abstract class Evt implements Jsonable {
    private static final long serialVersionUID = 1L;
  }

  public static class Appended extends Evt {

    private static final long serialVersionUID = 1L;

    private final String element;

    @JsonCreator
    public Appended(String element) {
      this.element = element;
    }

    public String getElement() {
      return element;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((element == null) ? 0 : element.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      Appended other = (Appended) obj;
      if (element == null) {
        if (other.element != null)
          return false;
      } else if (!element.equals(other.element))
        return false;
      return true;
    }

    @Override
    public String toString() {
      return "Appended [element=" + element + "]";
    }

  }

  public static class InPrependMode extends Evt {

    private static final long serialVersionUID = 1L;
    private static final InPrependMode instance = new InPrependMode();

    @JsonCreator
    public static InPrependMode instance() {
      return InPrependMode.instance;
    }

    private InPrependMode() {
    }

    @Override
    public String toString() {
      return "InPrependMode";
    }
  }

}
