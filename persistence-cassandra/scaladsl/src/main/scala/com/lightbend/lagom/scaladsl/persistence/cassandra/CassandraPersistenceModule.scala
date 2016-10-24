/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import com.google.inject.{ AbstractModule, Key }
import com.lightbend.lagom.internal.scaladsl.persistence.OffsetStore
import com.lightbend.lagom.internal.scaladsl.persistence.cassandra.{ CassandraConfigProvider, CassandraOffsetStore, CassandraPersistentEntityRegistry, CassandraReadSideImpl }
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry

/**
 * Guice module for the Persistence API.
 */
class CassandraPersistenceModule extends AbstractModule {
  override def configure(): Unit = {
    binder.bind(classOf[PersistentEntityRegistry]).to(classOf[CassandraPersistentEntityRegistry])
    binder.bind(classOf[CassandraSession])
    binder.bind(classOf[CassandraReadSide]).to(classOf[CassandraReadSideImpl])
    binder.bind(classOf[CassandraConfig]).toProvider(classOf[CassandraConfigProvider])
    binder.bind(classOf[CassandraOffsetStore])
    binder.bind(classOf[OffsetStore]).to(Key.get(classOf[CassandraOffsetStore]))
  }
}
