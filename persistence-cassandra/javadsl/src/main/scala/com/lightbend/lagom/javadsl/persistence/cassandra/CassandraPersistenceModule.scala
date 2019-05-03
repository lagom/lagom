/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.cassandra

import java.net.URI

import scala.concurrent.Future
import akka.actor.ActorSystem
import com.lightbend.lagom.internal.javadsl.persistence.cassandra._
import com.lightbend.lagom.internal.persistence.cassandra.CassandraOffsetStore
import com.lightbend.lagom.internal.persistence.cassandra.CassandraReadSideSettings
import com.lightbend.lagom.internal.persistence.cassandra.ServiceLocatorAdapter
import com.lightbend.lagom.internal.persistence.cassandra.ServiceLocatorHolder
import com.lightbend.lagom.javadsl.api.ServiceLocator
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.spi.persistence.OffsetStore
import javax.annotation.PostConstruct
import javax.inject.Inject
import play.api.Configuration
import play.api.Environment
import play.api.inject._

import scala.util.Try

/**
 * Guice module for the Persistence API.
 */
class CassandraPersistenceModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[CassandraPersistenceModule.InitServiceLocatorHolder].toSelf.eagerly(),
    bind[PersistentEntityRegistry].to[CassandraPersistentEntityRegistry],
    bind[CassandraSession].toSelf,
    bind[CassandraReadSide].to[CassandraReadSideImpl],
    bind[CassandraReadSideSettings].toSelf,
    bind[CassandraOffsetStore].to[JavadslCassandraOffsetStore],
    bind[OffsetStore].to(bind[CassandraOffsetStore])
  )

}

private[lagom] object CassandraPersistenceModule {

  class InitServiceLocatorHolder @Inject()(system: ActorSystem, injector: Injector) {

    // Guice doesn't support this, but other DI frameworks do.
    @PostConstruct
    def init(): Unit = {
      Try(injector.instanceOf[ServiceLocator]).foreach { locator =>
        ServiceLocatorHolder(system).setServiceLocator(new ServiceLocatorAdapter {
          override def locateAll(name: String): Future[List[URI]] = {
            import system.dispatcher
            import scala.compat.java8.FutureConverters._
            import scala.collection.JavaConverters._
            locator.locateAll(name).toScala.map(_.asScala.toList)
          }
        })
      }
    }
  }

}
