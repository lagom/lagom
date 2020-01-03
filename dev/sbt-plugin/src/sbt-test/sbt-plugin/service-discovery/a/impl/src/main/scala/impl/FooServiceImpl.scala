/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package impl

import com.lightbend.lagom.scaladsl.api.ServiceCall
import api.FooService
import scala.concurrent.duration._
import akka.discovery.Discovery
import akka.actor.ActorSystem

class FooServiceImpl(actorSystem: ActorSystem) extends FooService {

  implicit val ec = actorSystem.dispatcher

  override def foo = ServiceCall { _ =>
    val serviceDiscovery = Discovery(actorSystem).discovery
    serviceDiscovery.lookup("foo-service", 100.milliseconds).map { res =>
      res.serviceName
    }
  }
}
