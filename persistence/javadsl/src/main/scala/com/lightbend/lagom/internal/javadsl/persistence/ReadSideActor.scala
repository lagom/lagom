/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.persistence

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.Status
import akka.stream.javadsl.Source
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.RestartSource
import akka.stream.scaladsl.Sink
import akka.stream.KillSwitch
import akka.stream.KillSwitches
import akka.stream.Materializer
import akka.stream.scaladsl
import akka.util.Timeout
import akka.Done
import akka.NotUsed
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.javadsl.persistence._

import scala.compat.java8.FutureConverters._
import scala.concurrent.Future

private[lagom] object ReadSideActor {

  def props[Event <: AggregateEvent[Event]](
      tagName: String,
      config: ReadSideConfig,
      clazz: Class[Event],
      globalPrepareTask: ClusterStartupTask,
      eventStreamFactory: (AggregateEventTag[Event], Offset) => Source[akka.japi.Pair[Event, Offset], NotUsed],
      processor: () => ReadSideProcessor[Event]
  )(implicit mat: Materializer) =
    Props(
      new ReadSideActor[Event](
        tagName,
        config,
        clazz,
        globalPrepareTask,
        eventStreamFactory,
        processor
      )
    )

  case object Start

}

/**
 * Read side actor
 */
private[lagom] class ReadSideActor[Event <: AggregateEvent[Event]](
    tagName: String,
    config: ReadSideConfig,
    clazz: Class[Event],
    globalPrepareTask: ClusterStartupTask,
    eventStreamFactory: (AggregateEventTag[Event], Offset) => Source[akka.japi.Pair[Event, Offset], NotUsed],
    processorFactory: () => ReadSideProcessor[Event]
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

  override def preStart(): Unit = {
    super.preStart()
    implicit val timeout: Timeout = Timeout(config.globalPrepareTimeout)
    globalPrepareTask
      .askExecute()
      .map { _ =>
        Start
      }
      .pipeTo(self)
  }

  def receive: Receive = {
    case Start =>
      val tag = new AggregateEventTag(clazz, tagName)
      val backOffSource =
        RestartSource.withBackoff(
          config.minBackoff,
          config.maxBackoff,
          config.randomBackoffFactor
        ) { () =>
          val handler: ReadSideProcessor.ReadSideHandler[Event] = processorFactory().buildHandler()
          val futureOffset: Future[Offset]                      = handler.prepare(tag).toScala

          scaladsl.Source
            .fromFuture(futureOffset)
            .initialTimeout(config.offsetTimeout)
            .flatMapConcat { offset =>
              val eventStreamSource = eventStreamFactory(tag, offset).asScala
              val usersFlow         = handler.handle()
              eventStreamSource.via(usersFlow)
            }

        }

      val (killSwitch, streamDone) = backOffSource
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.both)
        .run()

      shutdown = Some(killSwitch)
      streamDone.pipeTo(self)

    case Done =>
      // This `Done` is materialization of the `Sink.ignore` above.
      throw new IllegalStateException("Stream terminated when it shouldn't")

    case Status.Failure(cause) =>
      // Crash if the globalPrepareTask or the event stream fail
      // This actor will be restarted by WorkerCoordinator
      throw cause

  }

}
