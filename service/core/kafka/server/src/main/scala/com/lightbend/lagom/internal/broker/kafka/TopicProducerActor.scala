/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.broker.kafka

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.Status
import akka.kafka.scaladsl.DiscoverySupport
import akka.kafka.scaladsl.{ Producer => ReactiveProducer }
import akka.kafka.ProducerMessage
import akka.kafka.ProducerSettings
import akka.pattern.pipe
import akka.persistence.query.{ Offset => AkkaOffset }
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.RestartSource
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Unzip
import akka.stream.scaladsl.Zip
import akka.stream.FlowShape
import akka.stream.KillSwitch
import akka.stream.KillSwitches
import akka.stream.Materializer
import akka.Done
import akka.NotUsed
import com.lightbend.lagom.internal.broker.kafka.TopicProducerActor.Start
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates
import com.lightbend.lagom.internal.spi.projection.ProjectionSpi
import com.lightbend.lagom.spi.persistence.OffsetDao
import com.lightbend.lagom.spi.persistence.OffsetStore
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

private[lagom] object TopicProducerActor {
  def props[Message](
      workerCoordinates: WorkerCoordinates,
      producerConfig: ProducerConfig,
      topicId: String,
      eventStreamFactory: (String, AkkaOffset) => Source[(Message, AkkaOffset), _],
      partitionKeyStrategy: Option[Message => String],
      serializer: Serializer[Message],
      offsetStore: OffsetStore
  )(implicit mat: Materializer, ec: ExecutionContext) =
    Props(
      new TopicProducerActor[Message](
        workerCoordinates,
        producerConfig,
        topicId,
        eventStreamFactory,
        partitionKeyStrategy,
        serializer,
        offsetStore
      )
    )

  case object Start
}

/**
 * The ProducerActor is activated remotely by a message with a tagname. That tagname identifies a shard
 * of a Persistent Entity which this actor will poll, then feed on a user Flow and finally publish into
 * Kafka. See also ReadSideActor.
 */
private[lagom] class TopicProducerActor[Message](
    workerCoordinates: WorkerCoordinates,
    producerConfig: ProducerConfig,
    topicId: String,
    eventStreamFactory: (String, AkkaOffset) => Source[(Message, AkkaOffset), _],
    partitionKeyStrategy: Option[Message => String],
    serializer: Serializer[Message],
    offsetStore: OffsetStore
)(implicit mat: Materializer, ec: ExecutionContext)
    extends Actor
    with ActorLogging {

  val tagName = workerCoordinates.tagName

  /** Switch used to terminate the on-going stream when this actor is stopped.*/
  private var shutdown: Option[KillSwitch] = None

  override def postStop(): Unit = {
    shutdown.foreach(_.shutdown())
  }

  override def preStart(): Unit = {
    super.preStart()
    self ! Start
  }

  def receive: Receive = {
    case Start => {
      val backoffSource: Source[Future[AkkaOffset], NotUsed] = {
        RestartSource.withBackoff(
          producerConfig.minBackoff,
          producerConfig.maxBackoff,
          producerConfig.randomBackoffFactor
        ) { () =>
          Source
            .future(eventualOffset(tagName))
            .initialTimeout(producerConfig.offsetTimeout)
            .flatMapConcat { offset =>
              log.debug("Kafka service for producer of [{}]", topicId)
              val eventStreamSource: Source[(Message, AkkaOffset), _] =
                eventStreamFactory(tagName, offset.loadedOffset)
                  .watchTermination() { (_, right: Future[Done]) =>
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

              val eventPublisherFlow: Flow[(Message, AkkaOffset), Future[AkkaOffset], Any] =
                eventsPublisherFlow(offset) // Return a Source[Future[Offset],_] where each produced element is a completed Offset.
              eventStreamSource             // read from DB + userFlow
                .map {
                  case (message, offset) =>
                    (
                      message,
                      ProjectionSpi.afterUserFlow(workerCoordinates.projectionName, workerCoordinates.tagName, offset)
                    )
                }
                .via(eventPublisherFlow) //  Kafka write + offset commit
                .map(_.map(offset => {
                  ProjectionSpi
                    .completedProcessing(workerCoordinates.projectionName, workerCoordinates.tagName, offset)
                  offset
                }))
            }
        }
      }

      val (killSwitch, streamDone) = backoffSource
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.both)
        .run()

      shutdown = Some(killSwitch)
      streamDone.pipeTo(self)
    }

    case Done =>
      // This `Done` is materialization of the `Sink.ignore` above.
      log.info("Kafka producer stream for topic {} was completed.", topicId)
      context.stop(self)

    case Status.Failure(e) =>
      // Crash if the globalPrepareTask or the event stream fail
      // This actor will be restarted by WorkerCoordinator
      throw e
  }

  /**
   * Every time we want to re/start the stream we need to locate the brokers and load the latest offset.
   * The returned future can fail when the offset store is unavailable or times out or when the brokers list
   * is empty or unconfigured, etc... In either case, the stream can't be built
   */
  private def eventualOffset(tagName: String): Future[OffsetDao] = {
    // TODO: review the OffsetStore API. I think `prepare()` does more than we need here. Ideally, prepare (create
    // schema and prepared statements) would be used when this actor starts and here we would only query for
    // the latest offset.
    offsetStore.prepare(s"topicProducer-$topicId", tagName)
  }

  private def eventsPublisherFlow(offsetDao: OffsetDao) =
    Flow.fromGraph(GraphDSL.create(kafkaFlowPublisher()) { implicit builder => publishFlow =>
      import GraphDSL.Implicits._
      val unzip = builder.add(Unzip[Message, AkkaOffset])
      val zip   = builder.add(Zip[Any, AkkaOffset])
      val offsetCommitter = builder.add(Flow.fromFunction[(Any, AkkaOffset), Future[AkkaOffset]] {
        case (_, akkaOffset) =>
          offsetDao.saveOffset(akkaOffset).map(done => akkaOffset)
      })

      unzip.out0 ~> publishFlow ~> zip.in0
      unzip.out1 ~> zip.in1
      zip.out ~> offsetCommitter.in
      FlowShape(unzip.in, offsetCommitter.out)
    })

  private def kafkaFlowPublisher(): Flow[Message, _, _] = {
    def keyOf(message: Message): String = {
      partitionKeyStrategy match {
        case Some(strategy) => strategy(message)
        case None           => null
      }
    }

    Flow[Message]
      .map { message =>
        ProducerMessage.Message(new ProducerRecord[String, Message](topicId, keyOf(message), message), NotUsed)
      }
      .via {
        ReactiveProducer.flexiFlow(producerSettings())
      }
  }

  private def producerSettings(): ProducerSettings[String, Message] = {
    val keySerializer = new StringSerializer

    val config = context.system.settings.config.getConfig(ProducerSettings.configPath)
    ProducerSettings(config, keySerializer, serializer)
      .withProperty("client.id", self.path.toStringWithoutAddress)
      .withEnrichAsync(
        DiscoverySupport.producerBootstrapServers(config)(context.system)
      )
  }
}
