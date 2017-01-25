/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra

import java.net.URI

import scala.concurrent.Future
import akka.actor.ActorSystem
import com.google.inject.{ Inject, TypeLiteral }
import com.google.inject.{ AbstractModule, Key }
import com.google.inject.matcher.AbstractMatcher
import com.google.inject.spi.InjectionListener
import com.google.inject.spi.TypeEncounter
import com.google.inject.spi.TypeListener
import com.lightbend.lagom.internal.javadsl.persistence.cassandra._
import com.lightbend.lagom.internal.persistence.cassandra.ServiceLocatorAdapter
import com.lightbend.lagom.internal.persistence.cassandra.ServiceLocatorHolder
import com.lightbend.lagom.javadsl.api.ServiceLocator
import com.lightbend.lagom.internal.persistence.cassandra.CassandraOffsetStore
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.spi.persistence.OffsetStore

/**
 * Guice module for the Persistence API.
 */
class CassandraPersistenceModule extends AbstractModule {
  override def configure(): Unit = {
    binder.bind(classOf[CassandraPersistenceModule.InitServiceLocatorHolder]).asEagerSingleton()
    binder.bind(classOf[PersistentEntityRegistry]).to(classOf[CassandraPersistentEntityRegistry])
    binder.bind(classOf[CassandraSession])
    binder.bind(classOf[CassandraReadSide]).to(classOf[CassandraReadSideImpl])
    binder.bind(classOf[CassandraConfig]).toProvider(classOf[CassandraConfigProvider])
    binder.bind(classOf[CassandraOffsetStore]).to(classOf[JavadslCassandraOffsetStore])
    binder.bind(classOf[OffsetStore]).to(Key.get(classOf[CassandraOffsetStore]))
    initServiceLocatorHolder()
  }

  private def initServiceLocatorHolder(): Unit = {
    val listener: TypeListener = new TypeListener {
      override def hear[I](typeLiteral: TypeLiteral[I], typeEncounter: TypeEncounter[I]): Unit = {
        typeEncounter.register(new InjectionListener[I] {
          override def afterInjection(i: I): Unit = {
            i.asInstanceOf[CassandraPersistenceModule.InitServiceLocatorHolder].init();
          }
        })
      }
    }
    val matcher = new AbstractMatcher[TypeLiteral[_]] {
      override def matches(typeLiteral: TypeLiteral[_]): Boolean = {
        return classOf[CassandraPersistenceModule.InitServiceLocatorHolder] == typeLiteral.getRawType;
      }
    }
    binder.bindListener(matcher, listener)
  }
}

private object CassandraPersistenceModule {

  private class InitServiceLocatorHolder @Inject() (system: ActorSystem) {

    @volatile private var serviceLocator: Option[ServiceLocator] = None
    @volatile private var env: Option[play.Environment] = None

    @Inject(optional = true) def setServiceLocator(_serviceLocator: ServiceLocator): Unit = {
      serviceLocator = Some(_serviceLocator)
    }

    @Inject(optional = true) def setEnvironment(_env: play.Environment): Unit = {
      env = Some(_env)
    }

    private[CassandraPersistenceModule] def init(): Unit = {
      serviceLocator.foreach { locator =>
        ServiceLocatorHolder(system).setServiceLocator(new ServiceLocatorAdapter {
          override def locate(name: String): Future[Option[URI]] = {
            import system.dispatcher
            import scala.compat.java8.FutureConverters._
            import scala.compat.java8.OptionConverters._
            locator.locate(name).toScala.map(_.asScala)
          }
        })
      }
    }
  }
}
