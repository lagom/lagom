/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
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
import akka.japi
import akka.persistence.query.Offset
import akka.persistence.query.{ Offset => AkkaOffset }
import akka.stream.FlowShape
import akka.stream.javadsl
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Unzip
import akka.stream.scaladsl.Zip
import com.lightbend.lagom.internal.persistence.ReadSideConfig
import com.lightbend.lagom.internal.persistence.cluster.ClusterStartupTask
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates
import com.lightbend.lagom.internal.spi.projection.ProjectionSpi
import com.lightbend.lagom.javadsl.persistence.AggregateEvent
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag
import com.lightbend.lagom.javadsl.persistence.Offset
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor
import com.lightbend.lagom.javadsl.persistence.{ Offset => LagomOffset }

import scala.compat.java8.FutureConverters._
import scala.concurrent.Future

private[lagom] object ReadSideActor {
  def props[Event <: AggregateEvent[Event]](
      workerCoordinates: WorkerCoordinates,
      config: ReadSideConfig,
      clazz: Class[Event],
      globalPrepareTask: ClusterStartupTask,
      eventStreamFactory: (AggregateEventTag[Event], LagomOffset) => Source[akka.japi.Pair[Event, LagomOffset], NotUsed],
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
    eventStreamFactory: (AggregateEventTag[Event], LagomOffset) => Source[akka.japi.Pair[Event, LagomOffset], NotUsed],
    processorFactory: () => ReadSideProcessor[Event]
)(implicit mat: Materializer)
    extends Actor
    with ActorLogging {
  import ReadSideActor._
  import akka.pattern.pipe
  import context.dispatcher

  private var shutdown: Option[KillSwitch] = None

  val tagName = workerCoordinates.tagName

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
      val backOffSource: scaladsl.Source[Done, NotUsed] =
        RestartSource.withBackoff(
          config.minBackoff,
          config.maxBackoff,
          config.randomBackoffFactor
        ) { () =>
          val handler: ReadSideProcessor.ReadSideHandler[Event] = processorFactory().buildHandler()
          val futureOffset: Future[LagomOffset]                 = handler.prepare(tag).toScala

          scaladsl.Source
            .future(futureOffset)
            .initialTimeout(config.offsetTimeout)
            .flatMapConcat { offset =>
              val eventStreamSource: scaladsl.Source[japi.Pair[Event, LagomOffset], NotUsed] =
                eventStreamFactory(tag, offset).asScala
              val userFlow: javadsl.Flow[japi.Pair[Event, LagomOffset], Done, _] =
                handler
                  .handle()
                  .asScala
                  .watchTermination() { (_, right) =>
                    right.recoverWith {
                      case t: Throwable =>
                        ProjectionSpi.failed(
                          context.system,
                          workerCoordinates.projectionName,
                          workerCoordinates.tagName,
                          t
                        )
                        right
                    }
                  }
                  .asJava

              if (config.withMetrics) {
                val wrappedFlow: Flow[japi.Pair[Event, LagomOffset], Done, NotUsed] = Flow[japi.Pair[Event, LagomOffset]]
                  .map { pair =>
                    (pair, OffsetAdapter.dslOffsetToOffset(pair.second))
                  }
                  .via(userFlowWrapper(workerCoordinates, userFlow.asScala))
                eventStreamSource.via(wrappedFlow)
              } else {
                eventStreamSource.via(userFlow)
              }
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
      userFlow: Flow[japi.Pair[Event, LagomOffset], Done, _]
  ): Flow[(japi.Pair[Event, LagomOffset], AkkaOffset), Done, _] =
    Flow.fromGraph(GraphDSL.create(userFlow) { implicit builder => wrappedFlow =>
      import GraphDSL.Implicits._
      val unzip = builder.add(Unzip[japi.Pair[Event, LagomOffset], AkkaOffset])
      val zip   = builder.add(Zip[Done, AkkaOffset])
      val metricsReporter: FlowShape[(Done, AkkaOffset), Done] = builder.add(Flow.fromFunction {
        case (_, akkaOffset) =>
          // TODO: in ReadSide processor we can't report `afterUserFlow` and `completedProcessing` separately
          //  as we do in TopicProducerActor, unless we moved the invocation of `afterUserFlow` to each
          //  particular ReadSideHandler (C* and JDBC).
          ProjectionSpi.afterUserFlow(workerCoordinates.projectionName, workerCoordinates.tagName, akkaOffset)
          ProjectionSpi.completedProcessing(
            workerCoordinates.projectionName,
            workerCoordinates.tagName,
            akkaOffset
          )
          Done
      })

      unzip.out0 ~> wrappedFlow ~> zip.in0
      unzip.out1 ~> zip.in1
      zip.out ~> metricsReporter.in
      FlowShape(unzip.in, metricsReporter.out)
    })

}
