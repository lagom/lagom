/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import scala.compat.java8.FutureConverters._
import java.util.Collections
import java.util.function.{ Function => JFunction }
import com.google.inject.AbstractModule
import com.lightbend.lagom.internal.persistence.cassandra.CassandraReadSideImpl
import com.lightbend.lagom.internal.persistence.cassandra.CassandraSessionImpl
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSide
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession
import com.lightbend.lagom.internal.persistence.PersistentEntityRegistryImpl
import com.lightbend.lagom.internal.persistence.cassandra.CassandraReadSideImpl
import com.lightbend.lagom.internal.persistence.cassandra.CassandraSessionImpl
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSide
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession
import akka.actor.ActorSystem
import com.google.inject.Provides
import com.lightbend.lagom.javadsl.api.ServiceLocator
import com.lightbend.lagom.internal.persistence.cassandra.ServiceLocatorHolder
import com.google.inject.Inject
import com.lightbend.lagom.internal.registry.ServiceRegistryService
import com.lightbend.lagom.internal.registry.ServiceRegistry
import play.api.Logger
import akka.NotUsed
import com.lightbend.lagom.javadsl.api.ServiceAcl
import javax.annotation.PostConstruct
import com.google.inject.matcher.Matcher
import com.google.inject.matcher.Matchers
import com.google.inject.spi.TypeListener
import com.google.inject.spi.TypeEncounter
import com.google.inject.TypeLiteral
import com.google.inject.spi.InjectionListener
import com.google.inject.matcher.AbstractMatcher
import java.util.function.Consumer
import com.typesafe.config.Config
import com.lightbend.lagom.internal.persistence.cassandra.ServiceLocatorSessionProvider
import scala.concurrent.Future

/**
 * Guice module for the Persistence API.
 */
class PersistenceModule extends AbstractModule {

  override def configure(): Unit = {
    binder.bind(classOf[PersistentEntityRegistry]).to(classOf[PersistentEntityRegistryImpl])
    binder.bind(classOf[CassandraSession]).to(classOf[CassandraSessionImpl])
    binder.bind(classOf[CassandraReadSide]).to(classOf[CassandraReadSideImpl])

    binder.bind(classOf[InitServiceLocatorHolder]).asEagerSingleton()
    initServiceRegistry()
  }

  private def initServiceRegistry(): Unit = {
    val listener: TypeListener = new TypeListener {
      override def hear[I](typeLiteral: TypeLiteral[I], typeEncounter: TypeEncounter[I]): Unit = {
        typeEncounter.register(new InjectionListener[I] {
          override def afterInjection(i: I): Unit = {
            i.asInstanceOf[InitServiceLocatorHolder].init();
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

private[lagom] object InitServiceLocatorHolder {
  def cassandraUrisFromConfig(system: ActorSystem): Set[(String, String)] = {
    val config = system.settings.config
    List("cassandra-journal", "cassandra-snapshot-store", "lagom.persistence.read-side.cassandra").flatMap { path =>
      val c = config.getConfig(path)
      if (c.getString("session-provider") == classOf[ServiceLocatorSessionProvider].getName) {
        val name = c.getString("cluster-id")
        val port = c.getInt("port")
        val uri = s"tcp://127.0.0.1:$port/$name"
        Some(name -> uri)
      } else None
    }.toSet
  }
}

private[lagom] class InitServiceLocatorHolder @Inject() (system: ActorSystem) {

  private var serviceLocator: Option[ServiceLocator] = None
  private var serviceRegistry: Option[ServiceRegistry] = None

  @Inject(optional = true) def setServiceLocator(locator: ServiceLocator): Unit = {
    serviceLocator = Some(locator)
  }

  @Inject(optional = true) def setServiceRegistry(registry: ServiceRegistry): Unit = {
    serviceRegistry = Some(registry)
  }

  def init(): Unit = {
    serviceRegistry match {
      case Some(registry) =>
        import InitServiceLocatorHolder.cassandraUrisFromConfig
        import system.dispatcher
        val results = cassandraUrisFromConfig(system).map {
          case (name, uri) =>
            val r = new ServiceRegistryService(uri, Collections.emptyList[ServiceAcl])
            registry.register().invoke(name, r).toScala.recover {
              case t =>
                Logger(getClass).error(s"Cassandra server name=[$name] couldn't be registered to the service locator.", t)
                NotUsed
            }
        }

        // make it visible when all registrations are done
        Future.sequence(results).onComplete {
          case _ => serviceLocator.foreach(ServiceLocatorHolder(system).setServiceLocator)
        }

      case None =>
        serviceLocator.foreach(ServiceLocatorHolder(system).setServiceLocator)
    }

  }

}

