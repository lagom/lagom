/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence

import akka.{ Done, NotUsed }
import akka.actor.{ Actor, ActorLogging, Props, Status }
import akka.stream.javadsl.Source
import akka.stream.scaladsl.{ Keep, Sink }
import akka.stream.{ KillSwitch, KillSwitches, Materializer }
import akka.util.Timeout
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.javadsl.persistence._

import scala.compat.java8.FutureConverters._
import scala.concurrent.duration.FiniteDuration

private[lagom] object ReadSideActor {

  def props[Event <: AggregateEvent[Event]](
    processor:            () => ReadSideProcessor[Event],
    eventStreamFactory:   (AggregateEventTag[Event], Offset) => Source[akka.japi.Pair[Event, Offset], NotUsed],
    clazz:                Class[Event],
    globalPrepareTask:    ClusterStartupTask,
    globalPrepareTimeout: FiniteDuration
  )(implicit mat: Materializer) = {
    Props(classOf[ReadSideActor[Event]], processor, eventStreamFactory, clazz, globalPrepareTask, globalPrepareTimeout, mat)
  }

  /**
   * Start processing from the given offset
   */
  case class Start(offset: Offset)
}

/**
 * Read side actor
 */
private[lagom] class ReadSideActor[Event <: AggregateEvent[Event]](
  processorFactory:     () => ReadSideProcessor[Event],
  eventStreamFactory:   (AggregateEventTag[Event], Offset) => Source[akka.japi.Pair[Event, Offset], NotUsed],
  clazz:                Class[Event],
  globalPrepareTask:    ClusterStartupTask,
  globalPrepareTimeout: FiniteDuration
)(implicit mat: Materializer) extends Actor with ActorLogging {
  import ReadSideActor._
  import akka.pattern.pipe
  import akka.pattern.ask
  import context.dispatcher

  private var shutdown: Option[KillSwitch] = None

  override def postStop: Unit = {
    shutdown.foreach(_.shutdown())
  }

  def receive = {
    case EnsureActive(tagName) =>

      val tag = AggregateEventTag.of(clazz, tagName)

      implicit val timeout = Timeout(globalPrepareTimeout)

      globalPrepareTask.askExecute() pipeTo self
      context become preparing(tag)
  }

  def preparing(tag: AggregateEventTag[Event]): Receive = {

    case Done =>
      val handler = processorFactory().buildHandler()
      handler.prepare(tag).toScala.map(Start) pipeTo self
      context become active(handler, tag)

    case Status.Failure(e) =>
      throw e

  }

  def active(handler: ReadSideProcessor.ReadSideHandler[Event], tag: AggregateEventTag[Event]): Receive = {

    case Start(offset) =>

      val (killSwitch, streamDone) = eventStreamFactory(tag, offset).asScala
        .viaMat(KillSwitches.single)(Keep.right)
        .via(handler.handle())
        .toMat(Sink.ignore)(Keep.both)
        .run()

      shutdown = Some(killSwitch)
      streamDone pipeTo self

    case EnsureActive(_) =>
    // Yes, we are active

    case Status.Failure(e) =>
      throw e

    case Done =>
      throw new IllegalStateException("Stream terminated when it shouldn't")

  }

}
