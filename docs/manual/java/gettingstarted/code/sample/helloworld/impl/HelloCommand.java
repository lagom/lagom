/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
 package sample.helloworld.impl;

import java.util.Optional;

import javax.annotation.Nullable;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;


public interface HelloCommand {
  public final class Hello implements HelloCommand, PersistentEntity.ReplyType<String> {
    public final String name;
    public final Optional<String> organization;

    public Hello(String name, Optional<String> organization) {
      this.name = name;
      this.organization = organization;
    }
  }
}
