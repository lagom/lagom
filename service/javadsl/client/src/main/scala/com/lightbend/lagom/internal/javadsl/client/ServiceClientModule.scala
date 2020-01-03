/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.client

import play.api.Configuration
import play.api.Environment
import play.api.inject.Binding
import play.api.inject.Module

class ServiceClientModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[JavadslWebSocketClient].toSelf,
    bind[JavadslServiceClientImplementor].toSelf,
    bind[ServiceClientLoader].toSelf
  )
}
