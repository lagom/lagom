package com.lightbend.lagom.scaladsl.akka.discovery

import java.net.URI

import akka.actor.ActorSystem
import akka.discovery.ServiceDiscovery
import com.lightbend.lagom.internal.client.AkkaDiscoveryHelper
import com.lightbend.lagom.scaladsl.api.{Descriptor, ServiceLocator}
import com.lightbend.lagom.scaladsl.client.{CircuitBreakerComponents, CircuitBreakersPanel, CircuitBreakingServiceLocator}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Akka discovery based implementation of the [[ServiceLocator]].
  */
class AkkaDiscoveryServiceLocator(circuitBreakers: CircuitBreakersPanel, actorSystem: ActorSystem)(implicit ec: ExecutionContext)
  extends CircuitBreakingServiceLocator(circuitBreakers) {

  private val helper: AkkaDiscoveryHelper = new AkkaDiscoveryHelper(
    actorSystem.settings.config.getConfig("lagom.akka.discovery"),
    ServiceDiscovery(actorSystem).discovery
  )

  override def locate(name: String, serviceCall: Descriptor.Call[_, _]): Future[Option[URI]] =
    helper.locate(name)

  override def locateAll(name: String, serviceCall: Descriptor.Call[_, _]): Future[List[URI]] =
    helper.locateAll(name).map(_.toList)
}

/**
  * Mix this into your application cake to get the Akka Discovery based implementation of the Lagom
  * [[ServiceLocator]].
  */
trait AkkaDiscoveryComponents extends CircuitBreakerComponents {
  lazy val serviceLocator: ServiceLocator =
    new AkkaDiscoveryServiceLocator(circuitBreakersPanel, actorSystem)(executionContext)
}
