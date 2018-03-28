/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import akka.cluster.Cluster
import akka.pattern.AskTimeoutException

import scala.concurrent.{ ExecutionContext, Future }

class PersistentEntityTracingConfig(
  val logClusterStateOnTimeout:    Boolean,
  val logCommandsPayloadOnTimeout: Boolean
)

trait PersistentEntityResultHandler {

  def mapResult[Cmd <: Object with PersistentEntity.ReplyType[_]](
    result:  Future[Any],
    command: Cmd
  )(implicit ec: ExecutionContext): Future[command.ReplyType] = {
    result.flatMap {
      case exc: Throwable =>
        // not using akka.actor.Status.Failure because it is using Java serialization
        Future.failed(exc)
      case result => Future.successful(result)
    }.asInstanceOf[Future[command.ReplyType]]
  }

}

object DefaultPersistentEntityResultHandler extends PersistentEntityResultHandler

class TracingPersistentEntityResultHandler(
  val cluster:       Cluster,
  val tracingConfig: PersistentEntityTracingConfig,
  val entityId:      String
) extends PersistentEntityResultHandler {

  override def mapResult[Cmd <: Object with PersistentEntity.ReplyType[_]](
    result:  Future[Any],
    command: Cmd
  )(
    implicit
    ec: ExecutionContext
  ): Future[command.ReplyType] =
    super.mapResult(result, command).recoverWith {
      case e: Throwable => convertFailure(e, command)
    }

  private def convertFailure[Reply, Cmd <: PersistentEntity.ReplyType[_]](failure: Throwable, command: Cmd): Future[command.ReplyType] = {
    if (failure.isInstanceOf[AskTimeoutException]) {
      if (this.tracingConfig.logClusterStateOnTimeout) {
        var detailedMessage = ""
        if (this.tracingConfig.logCommandsPayloadOnTimeout) detailedMessage = " with payload: " + command.toString
        cluster.system.log.error(
          failure,
          "Ask timeout when sending command to  " + entityId + detailedMessage + " cluster state :" + cluster.state
        )
      }
      if (this.tracingConfig.logCommandsPayloadOnTimeout) {
        val message = failure.getMessage + " with payload" + command
        Future.failed(new AskTimeoutException(message, failure.getCause))
      } else {
        Future.failed(failure)
      }

    } else Future.failed(failure)
  }

}
