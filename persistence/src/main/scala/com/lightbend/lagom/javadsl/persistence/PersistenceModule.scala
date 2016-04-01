/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import com.google.inject.AbstractModule
import com.google.inject.Inject
import com.google.inject.TypeLiteral
import com.google.inject.matcher.AbstractMatcher
import com.google.inject.spi.InjectionListener
import com.google.inject.spi.TypeEncounter
import com.google.inject.spi.TypeListener
import com.lightbend.lagom.internal.persistence.PersistentEntityRegistryImpl
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraConfig
import com.lightbend.lagom.internal.persistence.cassandra.CassandraConfigProvider
import com.lightbend.lagom.internal.persistence.cassandra.CassandraReadSideImpl
import com.lightbend.lagom.internal.persistence.cassandra.CassandraSessionImpl
import com.lightbend.lagom.internal.persistence.cassandra.ServiceLocatorHolder
import com.lightbend.lagom.javadsl.api.ServiceLocator
import com.lightbend.lagom.javadsl.persistence.PersistenceModule.InitServiceLocatorHolder
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSide
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession

import akka.actor.ActorSystem

/**
 * Guice module for the Persistence API.
 */
class PersistenceModule extends AbstractModule {
  override def configure(): Unit = {
    binder.bind(classOf[PersistentEntityRegistry]).to(classOf[PersistentEntityRegistryImpl])
    binder.bind(classOf[CassandraSession]).to(classOf[CassandraSessionImpl])
    binder.bind(classOf[CassandraReadSide]).to(classOf[CassandraReadSideImpl])
    binder.bind(classOf[CassandraConfig]).toProvider(classOf[CassandraConfigProvider])
    binder.bind(classOf[PersistenceModule.InitServiceLocatorHolder]).asEagerSingleton()
    initServiceLocatorHolder()
  }

  private def initServiceLocatorHolder(): Unit = {
    val listener: TypeListener = new TypeListener {
      override def hear[I](typeLiteral: TypeLiteral[I], typeEncounter: TypeEncounter[I]): Unit = {
        typeEncounter.register(new InjectionListener[I] {
          override def afterInjection(i: I): Unit = {
            i.asInstanceOf[PersistenceModule.InitServiceLocatorHolder].init();
          }
        })
      }
    }
    val matcher = new AbstractMatcher[TypeLiteral[_]] {
      override def matches(typeLiteral: TypeLiteral[_]): Boolean = {
        return classOf[InitServiceLocatorHolder] == typeLiteral.getRawType;
      }
    }
    binder.bindListener(matcher, listener)
  }
}

private object PersistenceModule {

  private class InitServiceLocatorHolder @Inject() (system: ActorSystem) {

    @volatile private var serviceLocator: Option[ServiceLocator] = None
    @volatile private var env: Option[play.Environment] = None

    @Inject(optional = true) def setServiceLocator(_serviceLocator: ServiceLocator): Unit = {
      serviceLocator = Some(_serviceLocator)
    }

    @Inject(optional = true) def setEnvironment(_env: play.Environment): Unit = {
      env = Some(_env)
    }

    private[PersistenceModule] def init(): Unit = {
      // This implementation assumes that if the service is started in DEV mode, then the service is being started
      // from within the Lagom development environment, via the `lagomRun` task. This is relevant because services
      // that use the persistence module will get the `cassandra-register` module injected on their classpath.
      // The `cassandra-register` module takes care of registering the Cassandra contact-points to the service locator,
      // so that the Cassandra contact-points can be successfully retrieved when they are looked up by the Akka
      // persistence internals (see `ServiceLocatorSessionProvider#lookupContactPoints`).
      // Therefore, in DEV mode, `ServiceLocatorHolder#setServiceLocator` is expected to be called only after the 
      // Cassandra contact-points have been fully registered to the service locator. In all other cases (i.e., Test or
      // Prod), we expect that the Cassandra contact-points are known by the service locator prior to start the service.
      env match {
        case Some(e) if e.isDev() => // nothing to do as `ServiceLocatorHolder#setServiceLocator` will be called by the bound RegisterCassandraContactPoints instance
        case _                    => serviceLocator.foreach(ServiceLocatorHolder(system).setServiceLocator)
      }
    }
  }
}
