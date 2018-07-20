/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.jpa;

import com.lightbend.lagom.internal.javadsl.persistence.jpa.JpaReadSideImpl;
import com.lightbend.lagom.internal.javadsl.persistence.jpa.JpaSessionImpl;
import play.api.Configuration;
import play.api.Environment;
import play.api.inject.Binding;
import play.api.inject.Module;
import scala.collection.Seq;

public class JpaPersistenceModule extends Module {
    @Override
    public Seq<Binding<?>> bindings(Environment environment, Configuration configuration) {
        return seq(
                bind(JpaSession.class).to(JpaSessionImpl.class),
                bind(JpaReadSide.class).to(JpaReadSideImpl.class)
        );
    }
}
