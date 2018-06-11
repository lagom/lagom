/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import com.lightbend.lagom.internal.javadsl.persistence.{ ReadSideConfigProvider, ReadSideImpl }
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import play.api.{ Configuration, Environment }
import play.api.inject.{ Binding, Module }

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
