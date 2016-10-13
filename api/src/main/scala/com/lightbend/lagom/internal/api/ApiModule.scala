/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api

import com.lightbend.lagom.internal.api.broker.{ InjectorTopicFactoryProvider, TopicFactoryProvider }
import play.api.{ Configuration, Environment }
import play.api.inject._

class ApiModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[TopicFactoryProvider].to[InjectorTopicFactoryProvider]
    )
  }
}
