/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.api

import com.lightbend.lagom.internal.javadsl.api.broker.{ InjectorTopicFactoryProvider, TopicFactoryProvider }
import play.api.inject._
import play.api.{ Configuration, Environment }

class JavadslApiModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[TopicFactoryProvider].to[InjectorTopicFactoryProvider]
    )
  }
}
