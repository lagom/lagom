/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import com.google.inject.AbstractModule
import com.lightbend.lagom.internal.javadsl.persistence.{ ReadSideConfigProvider, ReadSideImpl }
import com.lightbend.lagom.internal.persistence.ReadSideConfig

/**
 * Guice module for the Persistence API.
 */
class PersistenceModule extends AbstractModule {
  override def configure(): Unit = {
    binder.bind(classOf[ReadSide]).to(classOf[ReadSideImpl])
    binder.bind(classOf[ReadSideConfig]).toProvider(classOf[ReadSideConfigProvider])
  }

}
