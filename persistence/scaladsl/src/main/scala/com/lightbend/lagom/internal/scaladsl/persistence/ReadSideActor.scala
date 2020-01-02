/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.persistence

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.Status
import akka.cluster.sharding.ShardRegion.EntityId
import akka.persistence.query.Offset
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.RestartSource
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.KillSwitch
import akka.stream.KillSwitches
import akka.stream.Materializer
import akka.util.Timeout
import akka.Done
import akka.NotUsed
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.scaladsl.persistence._

private[lagom] object ReadSideActor {

  def props[Event <: AggregateEvent[Event]](
      config: ReadSideConfig,
      clazz: Class[Event],
      globalPrepareTask: ClusterStartupTask,
      eventStreamFactory: (AggregateEventTag[Event], Offset) => Source[EventStreamElement[Event], NotUsed],
      processor: () => ReadSideProcessor[Event]
  )(implicit mat: Materializer) = {
    Props(
      classOf[ReadSideActor[Event]],
      config,
      clazz,
      globalPrepareTask,
      eventStreamFactory,
      processor,
      mat
    )
  }

  case object Start

}

/**
 * Read side actor
 */
private[lagom] class ReadSideActor[Event <: AggregateEvent[Event]](
    config: ReadSideConfig,
    clazz: Class[Event],
    globalPrepareTask: ClusterStartupTask,
    eventStreamFactory: (AggregateEventTag[Event], Offset) => Source[EventStreamElement[Event], NotUsed],
    processor: () => ReadSideProcessor[Event]
)(implicit mat: Materializer)
    extends Actor
    with ActorLogging {

  import ReadSideActor._
  import akka.pattern.pipe
  import context.dispatcher

  private var shutdown: Option[KillSwitch] = None

  override def postStop: Unit = {
    shutdown.foreach(_.shutdown())
  }

  def receive = {
    case EnsureActive(tagName) =>
      implicit val timeout = Timeout(config.globalPrepareTimeout)
      globalPrepareTask
        .askExecute()
        .map { _ =>
          Start
        }
        .pipeTo(self)
      context.become(start(tagName))
  }

  def start(tagName: EntityId): Receive = {
    case Start =>
      val tag = new AggregateEventTag(clazz, tagName)
      val backoffSource =
        RestartSource.withBackoff(
          config.minBackoff,
          config.maxBackoff,
          config.randomBackoffFactor
        ) { () =>
          val handler      = processor().buildHandler()
          val futureOffset = handler.prepare(tag)
          Source
            .fromFuture(futureOffset)
            .initialTimeout(config.offsetTimeout)
            .flatMapConcat { offset =>
              val eventStreamSource = eventStreamFactory(tag, offset)
              val userlandFlow      = handler.handle()
              eventStreamSource.via(userlandFlow)
            }
        }

      val (killSwitch, streamDone) = backoffSource
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.both)
        .run()

      shutdown = Some(killSwitch)
      streamDone.pipeTo(self)

    case EnsureActive(_) =>
    // Yes, we are active

    case Done =>
      throw new IllegalStateException("Stream terminated when it shouldn't")

    case Status.Failure(cause) =>
      // Crash if the globalPrepareTask or the event stream fail
      // This actor will be restarted by ClusterDistribution
      throw cause

  }

}
