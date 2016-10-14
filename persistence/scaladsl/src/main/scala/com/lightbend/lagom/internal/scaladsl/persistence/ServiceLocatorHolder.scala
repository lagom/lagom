/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence

import akka.actor.{ ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.lightbend.lagom.javadsl.api.ServiceLocator // FIXME scaladsl?

private[lagom] object ServiceLocatorHolder extends ExtensionId[ServiceLocatorHolder] with ExtensionIdProvider {
  override def get(system: ActorSystem): ServiceLocatorHolder = super.get(system)

  override def lookup = ServiceLocatorHolder

  override def createExtension(system: ExtendedActorSystem): ServiceLocatorHolder =
    new ServiceLocatorHolder
}

private[lagom] class ServiceLocatorHolder extends Extension {
  @volatile private var _serviceLocator: Option[ServiceLocator] = None

  def serviceLocator: Option[ServiceLocator] = _serviceLocator

  def setServiceLocator(locator: ServiceLocator): Unit =
    _serviceLocator = Some(locator)
}
