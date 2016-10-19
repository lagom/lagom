/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.jdbc

import com.google.inject.{ AbstractModule, Key }
import com.lightbend.lagom.internal.scaladsl.persistence.OffsetStore
import com.lightbend.lagom.internal.scaladsl.persistence.jdbc.{ JdbcOffsetStore, JdbcPersistentEntityRegistry, JdbcReadSideImpl, JdbcSessionImpl }
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry

class JdbcPersistenceModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[JdbcReadSide]).to(classOf[JdbcReadSideImpl])
    bind(classOf[PersistentEntityRegistry]).to(classOf[JdbcPersistentEntityRegistry])
    bind(classOf[JdbcSession]).to(classOf[JdbcSessionImpl])
    bind(classOf[JdbcOffsetStore])
    bind(classOf[OffsetStore]).to(Key.get(classOf[JdbcOffsetStore]))
  }
}
