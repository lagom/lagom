/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence

import akka.actor.{ ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.lightbend.lagom.javadsl.api.ServiceLocator

import scala.concurrent.{ Future, Promise }
import scala.util.Success

private[lagom] object ServiceLocatorHolder extends ExtensionId[ServiceLocatorHolder] with ExtensionIdProvider {
  override def get(system: ActorSystem): ServiceLocatorHolder = super.get(system)

  override def lookup = ServiceLocatorHolder

  override def createExtension(system: ExtendedActorSystem): ServiceLocatorHolder =
    new ServiceLocatorHolder
}

private[lagom] class ServiceLocatorHolder extends Extension {
  @volatile private var _serviceLocator: Option[ServiceLocator] = None

  private val promise = Promise[ServiceLocator]()
  def serviceLocatorEventually: Future[ServiceLocator] = promise.future

  def serviceLocator: Option[ServiceLocator] = _serviceLocator

  def setServiceLocator(locator: ServiceLocator): Unit = {
    require(_serviceLocator.isEmpty, "Service locator has already been defined")

    _serviceLocator = Some(locator)
    promise.complete(Success(locator))
  }
}
