/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.cassandra

import com.google.inject.matcher.AbstractMatcher
import com.google.inject.spi.InjectionListener
import com.google.inject.spi.TypeEncounter
import com.google.inject.spi.TypeListener
import com.google.inject.AbstractModule
import com.google.inject.TypeLiteral

/**
 * Guice module for the Cassandra Persistence API.
 *
 * This serves one purpose, to invoke the @PostConstruct annotated init method on
 * InitServiceLocatorHolder, since Guice doesn't support @PostConstruct.
 */
class CassandraPersistenceGuiceModule extends AbstractModule {
  override def configure(): Unit = {
    initServiceLocatorHolder()
  }

  private def initServiceLocatorHolder(): Unit = {
    val listener: TypeListener = new TypeListener {
      override def hear[I](typeLiteral: TypeLiteral[I], typeEncounter: TypeEncounter[I]): Unit = {
        typeEncounter.register(new InjectionListener[I] {
          override def afterInjection(i: I): Unit = {
            i.asInstanceOf[CassandraPersistenceModule.InitServiceLocatorHolder].init()
          }
        })
      }
    }
    val matcher = new AbstractMatcher[TypeLiteral[_]] {
      override def matches(typeLiteral: TypeLiteral[_]): Boolean = {
        classOf[CassandraPersistenceModule.InitServiceLocatorHolder] == typeLiteral.getRawType
      }
    }
    binder.bindListener(matcher, listener)
  }
}
