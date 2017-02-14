/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jdbc

import com.google.inject.{ AbstractModule, Key }
import com.lightbend.lagom.internal.javadsl.persistence.jdbc.{ JavadslJdbcOffsetStore, JdbcPersistentEntityRegistry, JdbcReadSideImpl, JdbcSessionImpl }
import com.lightbend.lagom.internal.persistence.jdbc.SlickOffsetStore
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.spi.persistence.OffsetStore

class JdbcPersistenceModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[JdbcReadSide]).to(classOf[JdbcReadSideImpl])
    bind(classOf[PersistentEntityRegistry]).to(classOf[JdbcPersistentEntityRegistry])
    bind(classOf[JdbcSession]).to(classOf[JdbcSessionImpl])
    bind(classOf[SlickOffsetStore]).to(classOf[JavadslJdbcOffsetStore])
    bind(classOf[OffsetStore]).to(Key.get(classOf[SlickOffsetStore]))
  }
}
