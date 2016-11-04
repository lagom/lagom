/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jdbc

import com.google.inject.{ AbstractModule, Key }
import com.lightbend.lagom.internal.javadsl.persistence.jdbc.{ JdbcOffsetStore, JdbcPersistentEntityRegistry, JdbcReadSideImpl, JdbcSessionImpl }
import com.lightbend.lagom.internal.persistence.OffsetStore
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry

class JdbcPersistenceModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[JdbcReadSide]).to(classOf[JdbcReadSideImpl])
    bind(classOf[PersistentEntityRegistry]).to(classOf[JdbcPersistentEntityRegistry])
    bind(classOf[JdbcSession]).to(classOf[JdbcSessionImpl])
    bind(classOf[JdbcOffsetStore])
    bind(classOf[OffsetStore]).to(Key.get(classOf[JdbcOffsetStore]))
  }
}
