/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence

import akka.actor.Status.Failure
import akka.{ Done, NotUsed }
import akka.actor.{ Actor, ActorLogging, ActorRef, Props, Status }
import akka.stream.javadsl.Source
import akka.stream.scaladsl.{ Keep, Sink }
import akka.stream.{ KillSwitch, KillSwitches, Materializer }
import akka.util.Timeout
import com.lightbend.lagom.internal.persistence.GlobalPrepareReadSideActor.Prepare
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.javadsl.persistence._

import scala.compat.java8.FutureConverters._
import scala.concurrent.duration.FiniteDuration

private[lagom] object ReadSideActor {

  def props[Event <: AggregateEvent[Event]](
    processor:            () => ReadSideProcessor[Event],
    eventStreamFactory:   (AggregateEventTag[Event], Offset) => Source[akka.japi.Pair[Event, Offset], NotUsed],
    clazz:                Class[Event],
    globalPrepare:        ActorRef,
    globalPrepareTimeout: FiniteDuration
  )(implicit mat: Materializer) = {
    Props(classOf[ReadSideActor[Event]], processor, eventStreamFactory, clazz, globalPrepare, globalPrepareTimeout, mat)
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
  globalPrepare:        ActorRef,
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

      globalPrepare ? Prepare pipeTo self
      context become preparing(tag)
  }

  def preparing(tag: AggregateEventTag[Event]): Receive = {

    case Done =>
      val handler = processorFactory().buildHandler()
      handler.prepare(tag).toScala.map(Start(_)) pipeTo self
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

private[lagom] object GlobalPrepareReadSideActor {

  /**
   * Ask the actor if we are prepared yet.
   *
   * The actor will send [[akka.Done]] in response.
   */
  case object Prepare

  def props(processorFactory: () => ReadSideProcessor[_], prepareTimeout: FiniteDuration) = {
    Props(classOf[GlobalPrepareReadSideActor], processorFactory, prepareTimeout)
  }
}

private[lagom] class GlobalPrepareReadSideActor(processorFactory: () => ReadSideProcessor[_], prepareTimeout: FiniteDuration) extends Actor {

  import GlobalPrepareReadSideActor._

  import akka.pattern.ask
  import akka.pattern.pipe

  import context.dispatcher

  override def preStart(): Unit = {
    // We let the ask pattern handle the timeout, by asking ourselves to do the prepare and piping the result back to
    // ourselves
    implicit val timeout = Timeout(prepareTimeout)
    self ? Prepare pipeTo self
  }

  def receive = {
    case Prepare =>
      processorFactory().buildHandler().globalPrepare().toScala pipeTo self
      context become preparing(List(sender()))
  }

  def preparing(outstandingQueries: List[ActorRef]): Receive = {
    case Prepare =>
      context become preparing(sender() :: outstandingQueries)

    case Done =>
      outstandingQueries foreach { requester =>
        requester ! Done
      }
      context become prepared

    case failure @ Failure(e) =>
      outstandingQueries foreach { requester =>
        requester ! failure
      }
      // If we failed to prepare, crash
      throw e
  }

  def prepared: Receive = {
    case Prepare =>
      sender() ! Done

    case Done =>
    // We do expect to receive Done once prepared since we initially asked ourselves to prepare
  }

}
