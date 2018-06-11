/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.server

import play.api.{ Configuration, Environment }
import play.api.inject.{ Binding, Module }

class ServerModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[JavadslServerBuilder].toSelf
  )
}
