package com.lightbend.lagom.scaladsl.server

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import akka.management.scaladsl.AkkaManagement

trait AkkaManagementComponents {

  def actorSystem: ActorSystem

  AkkaManagement(actorSystem.asInstanceOf[ExtendedActorSystem]).start()

}
