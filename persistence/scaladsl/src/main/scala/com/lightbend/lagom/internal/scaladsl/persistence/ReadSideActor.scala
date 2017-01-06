/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence

import akka.{ Done, NotUsed }
import akka.actor.{ Actor, ActorLogging, Props, Status }
import akka.persistence.query.Offset
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.{ Keep, Sink }
import akka.stream.{ KillSwitch, KillSwitches, Materializer }
import akka.util.Timeout
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.scaladsl.persistence._

import scala.concurrent.duration.FiniteDuration

private[lagom] object ReadSideActor {

  def props[Event <: AggregateEvent[Event]](
    processor:            () => ReadSideProcessor[Event],
    eventStreamFactory:   (AggregateEventTag[Event], Offset) => Source[EventStreamElement[Event], NotUsed],
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
  eventStreamFactory:   (AggregateEventTag[Event], Offset) => Source[EventStreamElement[Event], NotUsed],
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

      val tag = new AggregateEventTag(clazz, tagName)

      implicit val timeout = Timeout(globalPrepareTimeout)

      globalPrepareTask.askExecute() pipeTo self
      context become preparing(tag)
  }

  def preparing(tag: AggregateEventTag[Event]): Receive = {

    case Done =>
      val handler = processorFactory().buildHandler
      handler.prepare(tag).map(Start(_)) pipeTo self
      context become active(handler, tag)

    case Status.Failure(e) =>
      throw e

  }

  def active(handler: ReadSideProcessor.ReadSideHandler[Event], tag: AggregateEventTag[Event]): Receive = {

    case Start(offset) =>

      val (killSwitch, streamDone) = eventStreamFactory(tag, offset)
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
