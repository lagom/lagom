/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.persistence

import akka.Done
import akka.NotUsed
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.Status
import akka.persistence.query.{ Offset => AkkaOffset }
import akka.stream.FlowShape
import akka.stream.KillSwitch
import akka.stream.KillSwitches
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.RestartSource
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Unzip
import akka.stream.scaladsl.Zip
import akka.util.Timeout
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates
import com.lightbend.lagom.internal.spi.projection.ProjectionSpi
import com.lightbend.lagom.scaladsl.persistence._

import scala.concurrent.Future

private[lagom] object ReadSideActor {
  def props[Event <: AggregateEvent[Event]](
      workerCoordinates: WorkerCoordinates,
      config: ReadSideConfig,
      clazz: Class[Event],
      globalPrepareTask: ClusterStartupTask,
      eventStreamFactory: (AggregateEventTag[Event], AkkaOffset) => Source[EventStreamElement[Event], NotUsed],
      processor: () => ReadSideProcessor[Event]
  )(implicit mat: Materializer) =
    Props(
      new ReadSideActor[Event](
        workerCoordinates,
        config,
        clazz,
        globalPrepareTask,
        eventStreamFactory,
        processor
      )
    )

  case object Prepare
  case object Start
}

/**
 * Read side actor
 */
private[lagom] class ReadSideActor[Event <: AggregateEvent[Event]](
    workerCoordinates: WorkerCoordinates,
    config: ReadSideConfig,
    clazz: Class[Event],
    globalPrepareTask: ClusterStartupTask,
    eventStreamFactory: (AggregateEventTag[Event], AkkaOffset) => Source[EventStreamElement[Event], NotUsed],
    processor: () => ReadSideProcessor[Event]
)(implicit mat: Materializer)
    extends Actor
    with ActorLogging {
  import ReadSideActor._
  import akka.pattern.pipe
  import context.dispatcher

  val tagName = workerCoordinates.tagName

  /** Switch used to terminate the on-going stream when this actor is stopped.*/
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
      val backOffSource: Source[AkkaOffset, NotUsed] =
        RestartSource.withBackoff(
          config.minBackoff,
          config.maxBackoff,
          config.randomBackoffFactor
        ) { () =>
          val handler: ReadSideProcessor.ReadSideHandler[Event] = processor().buildHandler()
          val futureAkkaOffset: Future[AkkaOffset]              = handler.prepare(tag)

          Source
            .future(futureAkkaOffset)
            .initialTimeout(config.offsetTimeout)
            .flatMapConcat { offset =>
              val eventStreamSource: Source[EventStreamElement[Event], NotUsed] = eventStreamFactory(tag, offset)
              val userFlow                                                      = handler.handle()
              val wrappedFlow = Flow[EventStreamElement[Event]]
                .map { ese =>
                  (ese, ese.offset)
                }
                .via(userFlowWrapper(workerCoordinates, userFlow))

              eventStreamSource.via(wrappedFlow)
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

  private def userFlowWrapper(
      workerCoordinates: WorkerCoordinates,
      userFlow: Flow[EventStreamElement[Event], Done, NotUsed]
  ): Flow[(EventStreamElement[Event], AkkaOffset), AkkaOffset, NotUsed] =
    Flow.fromGraph(GraphDSL.create(userFlow) { implicit builder => wrappedFlow =>
      import GraphDSL.Implicits._
      val unzip = builder.add(Unzip[EventStreamElement[Event], AkkaOffset])
      val zip   = builder.add(Zip[Done, AkkaOffset])
      val metricsReporter: FlowShape[(Done, AkkaOffset), AkkaOffset] = builder.add(Flow.fromFunction {
        e: (Done, AkkaOffset) =>
          // TODO: in ReadSide processor we can't report `afterUserFlow` and `completedProcessing` separately
          //  as we do in TopicProducerActor, unless we moved the invocation of `afterUserFlow` to each
          //  particular ReadSideImpl (C* and JDBC).
          ProjectionSpi.afterUserFlow(workerCoordinates.projectionName, e._2)
          ProjectionSpi.completedProcessing(Future(e._2), context.dispatcher)
          e._2
      })

      unzip.out0 ~> wrappedFlow ~> zip.in0
      unzip.out1 ~> zip.in1
      zip.out ~> metricsReporter.in
      FlowShape(unzip.in, metricsReporter.out)
    })

}
