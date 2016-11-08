/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import com.google.inject.{ AbstractModule, Inject, TypeLiteral }
import com.google.inject.matcher.AbstractMatcher
import com.google.inject.spi.InjectionListener
import com.google.inject.spi.TypeEncounter
import com.google.inject.spi.TypeListener
import com.lightbend.lagom.javadsl.api.ServiceLocator
import com.lightbend.lagom.javadsl.persistence.PersistenceModule.InitServiceLocatorHolder
import akka.actor.ActorSystem
import com.lightbend.lagom.internal.javadsl.persistence.{ ReadSideConfigProvider, ReadSideImpl }
import com.lightbend.lagom.internal.persistence.{ ReadSideConfig, ServiceLocatorHolder }

/**
 * Guice module for the Persistence API.
 */
class PersistenceModule extends AbstractModule {
  override def configure(): Unit = {
    binder.bind(classOf[PersistenceModule.InitServiceLocatorHolder]).asEagerSingleton()
    binder.bind(classOf[ReadSide]).to(classOf[ReadSideImpl])
    binder.bind(classOf[ReadSideConfig]).toProvider(classOf[ReadSideConfigProvider])
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
      serviceLocator.foreach(ServiceLocatorHolder(system).setServiceLocator)
    }
  }
}
