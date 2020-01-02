/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.api

import com.lightbend.lagom.internal.javadsl.api.broker.InjectorTopicFactoryProvider
import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactoryProvider
import play.api.inject._
import play.api.Configuration
import play.api.Environment

class JavadslApiModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
    Seq(
      bind[TopicFactoryProvider].to[InjectorTopicFactoryProvider]
    )
  }
}
