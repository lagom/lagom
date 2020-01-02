/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.server

import play.api.Configuration
import play.api.Environment
import play.api.inject.Binding
import play.api.inject.Module

class ServerModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[JavadslServerBuilder].toSelf
  )
}
