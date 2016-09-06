package com.lightbend.lagom.javadsl.persistence.jdbc

import com.google.inject.AbstractModule
import com.lightbend.lagom.internal.persistence.jdbc.{JdbcPersistentEntityRegistry, JdbcReadSideImpl}
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry

class JdbcPersistenceModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[JdbcReadSide]).to(classOf[JdbcReadSideImpl])
    bind(classOf[PersistentEntityRegistry]).to(classOf[JdbcPersistentEntityRegistry])
  }
}
