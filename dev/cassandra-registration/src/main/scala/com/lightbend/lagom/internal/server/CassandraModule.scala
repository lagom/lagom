/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.server

import java.util.Collections

import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.collection.JavaConverters._

import com.google.inject.AbstractModule
import com.google.inject.Inject
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraConfig
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraContactPoint
import com.lightbend.lagom.internal.persistence.cassandra.ServiceLocatorHolder
import com.lightbend.lagom.internal.registry.ServiceRegistry
import com.lightbend.lagom.internal.registry.ServiceRegistryService
import com.lightbend.lagom.javadsl.api.ServiceAcl
import com.lightbend.lagom.javadsl.api.ServiceLocator

import akka.NotUsed
import akka.actor.ActorSystem
import play.api.Logger

class CassandraModule extends AbstractModule {
  override def configure(): Unit = {
    binder.bind(classOf[CassandraModule.RegisterCassandraContactPoints]).asEagerSingleton()
  }
}

object CassandraModule {
  // CassandraConfig is bound by the persistence module. Because the cassandra-registration module is injected only if the 
  // persistence module is in the classpath, we can assume the `CassandraConfig` interface is binded to a concrete instance.
  // If that isn't the case, then there is a bug, and we should fail fast, as the lack of configuration will lead to further 
  // issues if we don't (in particular, the service locator instance won't be set on the `ServiceLocatorHolder`, and that will 
  // make it impossible to work with Cassandra).
  private class RegisterCassandraContactPoints @Inject() (config: CassandraConfig, registry: ServiceRegistry, serviceLocator: ServiceLocator, system: ActorSystem)(implicit ec: ExecutionContext) {

    val registered = config.uris.asScala.map {
      case contactPoint: CassandraContactPoint =>
        val r = new ServiceRegistryService(contactPoint.uri, Collections.emptyList[ServiceAcl])
        registry.register(contactPoint.name).invoke(r).toScala.recover {
          case t =>
            Logger(getClass).error(s"Cassandra server name=[${contactPoint.name}] couldn't be registered to the service locator.", t)
            NotUsed
        }
    }

    // make it visible when all registrations are done
    Future.sequence(registered).onComplete {
      case _ => ServiceLocatorHolder(system).setServiceLocator(serviceLocator)
    }
  }
}
