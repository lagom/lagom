/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import scala.concurrent.{ Future, Promise }
import scala.util.Success
import akka.actor.{ ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import java.net.URI

import scala.concurrent.duration._
import scala.util.control.NoStackTrace

/**
 * The `ServiceLocatorHolder` is used by the `ServiceLocatorSessionProvider` to
 * locate the contact point address of the Cassandra cluster via the `ServiceLocator`.
 * The `ServiceLocator` is provided via dependency injection and `ServiceLocatorSessionProvider`
 * is created by `akka-persistence-cassandra` from configuration. To bridge those two worlds
 * this Akka extension is used so that the `ServiceLocatorSessionProvider` can use the
 * `ServiceLocator`.
 */
private[lagom] object ServiceLocatorHolder extends ExtensionId[ServiceLocatorHolder] with ExtensionIdProvider {
  override def get(system: ActorSystem): ServiceLocatorHolder = super.get(system)

  override def lookup = ServiceLocatorHolder

  override def createExtension(system: ExtendedActorSystem): ServiceLocatorHolder =
    new ServiceLocatorHolder(system)
}

private[lagom] class ServiceLocatorHolder(system: ExtendedActorSystem) extends Extension {
  @volatile private var _serviceLocator: Option[ServiceLocatorAdapter] = None

  private val promisedServiceLocator = Promise[ServiceLocatorAdapter]()

  private implicit val exCtx = system.dispatcher
  private val delayed = akka.pattern.after(10.seconds, using = system.scheduler) {
    Future.failed(new NoServiceLocatorException("Timed out."))
  }

  def serviceLocatorEventually: Future[ServiceLocatorAdapter] =
    Future firstCompletedOf Seq(promisedServiceLocator.future, delayed)

  def setServiceLocator(locator: ServiceLocatorAdapter): Unit = {
    require(_serviceLocator.isEmpty, "Service locator has already been defined")

    _serviceLocator = Some(locator)
    promisedServiceLocator.complete(Success(locator))
  }
}

private[lagom] final class NoServiceLocatorException(msg: String) extends RuntimeException(msg) with NoStackTrace

/**
 * scaladsl and javadsl specific implementations
 */
private[lagom] trait ServiceLocatorAdapter {
  def locate(name: String): Future[Option[URI]]
}

