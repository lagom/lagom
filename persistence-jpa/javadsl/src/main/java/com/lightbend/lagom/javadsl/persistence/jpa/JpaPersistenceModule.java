/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.jpa;

import com.lightbend.lagom.internal.javadsl.persistence.jpa.JpaReadSideImpl;
import com.lightbend.lagom.internal.javadsl.persistence.jpa.JpaSessionImpl;
import com.typesafe.config.Config;
import play.inject.Module;

import java.util.Arrays;
import java.util.List;

public class JpaPersistenceModule extends Module {
  @Override
  public List<play.inject.Binding<?>> bindings(play.Environment environment, Config config) {
    return Arrays.asList(
        bindClass(JpaSession.class).to(JpaSessionImpl.class),
        bindClass(JpaReadSide.class).to(JpaReadSideImpl.class));
  }
}
