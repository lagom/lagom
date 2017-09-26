/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence

import akka.actor.{ Actor, ActorLogging, Props }
import akka.cluster.sharding.ShardRegion.EntityId
import akka.stream.javadsl.Source
import akka.stream.scaladsl.{ Keep, RestartSource, Sink }
import akka.stream.{ KillSwitch, KillSwitches, Materializer, scaladsl }
import akka.util.Timeout
import akka.{ Done, NotUsed }
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.javadsl.persistence._

import scala.compat.java8.FutureConverters._

private[lagom] object ReadSideActor {

  def props[Event <: AggregateEvent[Event]](
    config:             ReadSideConfig,
    clazz:              Class[Event],
    globalPrepareTask:  ClusterStartupTask,
    eventStreamFactory: (AggregateEventTag[Event], Offset) => Source[akka.japi.Pair[Event, Offset], NotUsed],
    processor:          () => ReadSideProcessor[Event]
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
  config:             ReadSideConfig,
  clazz:              Class[Event],
  globalPrepareTask:  ClusterStartupTask,
  eventStreamFactory: (AggregateEventTag[Event], Offset) => Source[akka.japi.Pair[Event, Offset], NotUsed],
  processorFactory:   () => ReadSideProcessor[Event]
)(implicit mat: Materializer) extends Actor with ActorLogging {

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
      globalPrepareTask.askExecute().map { _ => Start } pipeTo self
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
          val handler: ReadSideProcessor.ReadSideHandler[Event] = processorFactory().buildHandler()
          val futureOffset = handler.prepare(tag).toScala
          scaladsl.Source.fromFuture(futureOffset).flatMapConcat {
            offset =>
              val eventStreamSource = eventStreamFactory(tag, offset).asScala
              val userlandFlow = handler.handle()
              eventStreamSource.via(userlandFlow)
          }
        }

      val (killSwitch, streamDone) = backoffSource
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.both)
        .run()

      shutdown = Some(killSwitch)
      streamDone pipeTo self

    case EnsureActive(_) =>
    // Yes, we are active

    case Done =>
      throw new IllegalStateException("Stream terminated when it shouldn't")

  }

}
