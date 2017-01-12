/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import akka.actor.{ ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import scala.concurrent.Future
import java.net.URI

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
    new ServiceLocatorHolder
}

private[lagom] class ServiceLocatorHolder extends Extension {
  @volatile private var _serviceLocator: Option[ServiceLocatorAdapter] = None

  def serviceLocator: Option[ServiceLocatorAdapter] = _serviceLocator

  def setServiceLocator(locator: ServiceLocatorAdapter): Unit =
    _serviceLocator = Some(locator)
}

/**
 * scaladsl and javadsl specific implementations
 */
private[lagom] trait ServiceLocatorAdapter {
  def locate(name: String): Future[Option[URI]]
}
