/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.server

import akka.actor.{ ActorSystem, ExtendedActorSystem }
import akka.management.scaladsl.AkkaManagement
import javax.inject.{ Inject, Provider, Singleton }
import play.api.inject.{ Binding, Module }
import play.api.{ Configuration, Environment }

class AkkaManagementModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[AkkaManagement].toProvider[AkkaManagementProvider].eagerly()
  )

}

@Singleton
class AkkaManagementProvider @Inject() (system: ActorSystem) extends Provider[AkkaManagement] {
  override def get(): AkkaManagement = {
    val akkaManagement = AkkaManagement(system.asInstanceOf[ExtendedActorSystem])
    akkaManagement.start()
    akkaManagement
  }
}