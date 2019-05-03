/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence

import com.lightbend.lagom.internal.javadsl.persistence.ReadSideConfigProvider
import com.lightbend.lagom.internal.javadsl.persistence.ReadSideImpl
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import play.api.Configuration
import play.api.Environment
import play.api.inject.Binding
import play.api.inject.Module

/**
 * Guice module for the Persistence API.
 */
class PersistenceModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[ReadSideImpl].toSelf,
    bind[ReadSide].to(bind[ReadSideImpl]),
    bind[ReadSideConfig].toProvider[ReadSideConfigProvider]
  )

}
