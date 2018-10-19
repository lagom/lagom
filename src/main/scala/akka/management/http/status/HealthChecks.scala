/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.management.http.status

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import akka.management.http.{ManagementRouteProvider, ManagementRouteProviderSettings}
import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}
import akka.cluster.{Cluster, MemberStatus}
import akka.event.{Logging, LoggingAdapter}


class HealthChecks(system: ExtendedActorSystem) extends ManagementRouteProvider {

  val log: LoggingAdapter = Logging(system, getClass)
  val cluster = Cluster(system)

  private val readyStates: Set[MemberStatus] =
    Set(
      MemberStatus.Joining,
      MemberStatus.WeaklyUp,
      MemberStatus.Up,
      MemberStatus.Leaving,
      MemberStatus.Exiting)



  def routes(settings: ManagementRouteProviderSettings): Route = pathPrefix("status") {
    import system.dispatcher

    concat(
      path("ready") {
        get {
          val selfState = cluster.selfMember.status
          if (readyStates.contains(selfState)) {
            log.debug("Available, cluster status: {}", selfState)
            complete(StatusCodes.OK)
          } else {
            log.debug("Not yet available: {}", selfState)
            complete(StatusCodes.InternalServerError)
          }
        }
      },
      path("alive") {
        get {
          // When Akka HTTP can respond to requests, that is sufficient
          // to consider ourselves 'live': we don't want K8s to kill us even
          // when we're in the process of shutting down (only stop sending
          // us traffic, which is done due to the readyness check then failing)
          complete(StatusCodes.OK)
        }
      })
  }
}

object HealthChecks extends ExtensionId[HealthChecks] with ExtensionIdProvider {
  override def lookup: HealthChecks.type = HealthChecks

  override def get(system: ActorSystem): HealthChecks = super.get(system)

  override def createExtension(system: ExtendedActorSystem): HealthChecks = new HealthChecks(system)
}
