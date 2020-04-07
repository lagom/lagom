/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.broker.kafka

import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.{ Producer => ReactiveProducer }
import akka.stream.scaladsl.GraphDSL
import akka.actor.Status
import akka.stream.scaladsl.Source
import akka.pattern.pipe
import akka.stream.scaladsl.Flow

import scala.concurrent.Future
import akka.stream.scaladsl.Sink

import scala.concurrent.ExecutionContext
import com.lightbend.lagom.internal.api.UriUtils
import com.lightbend.lagom.spi.persistence.OffsetDao
import akka.NotUsed
import akka.actor.ActorLogging
import akka.stream.scaladsl.Unzip
import org.apache.kafka.clients.producer.ProducerRecord
import akka.stream.Materializer
import akka.stream.scaladsl.Keep
import akka.stream.KillSwitches
import org.apache.kafka.common.serialization.StringSerializer
import akka.actor.Props
import org.apache.kafka.common.serialization.Serializer
import akka.stream.KillSwitch
import com.lightbend.lagom.spi.persistence.OffsetStore
import akka.stream.FlowShape
import akka.Done
import akka.actor.Actor
import akka.stream.scaladsl.Zip
import java.net.URI

import akka.kafka.ProducerMessage
import akka.persistence.query.Offset
import akka.persistence.query.{ Offset => AkkaOffset }
import akka.stream.scaladsl.RestartSource
import com.lightbend.lagom.internal.broker.kafka.TopicProducerActor.Start
import com.lightbend.lagom.internal.projection.ProjectionRegistryActor.WorkerCoordinates
import com.lightbend.lagom.internal.spi.projection.ProjectionSpi

private[lagom] object TopicProducerActor {
  def props[Message](
      workerCoordinates: WorkerCoordinates,
      kafkaConfig: KafkaConfig,
      producerConfig: ProducerConfig,
      locateService: String => Future[Seq[URI]],
      topicId: String,
      eventStreamFactory: (String, AkkaOffset) => Source[(Message, AkkaOffset), _],
      partitionKeyStrategy: Option[Message => String],
      serializer: Serializer[Message],
      offsetStore: OffsetStore
  )(implicit mat: Materializer, ec: ExecutionContext) =
    Props(
      new TopicProducerActor[Message](
        workerCoordinates,
        kafkaConfig,
        producerConfig,
        locateService,
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
    kafkaConfig: KafkaConfig,
    producerConfig: ProducerConfig,
    locateService: String => Future[Seq[URI]],
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
          val brokersAndOffset: Future[(String, OffsetDao)] = eventualBrokersAndOffset(tagName)
          Source
            .future(brokersAndOffset)
            .initialTimeout(producerConfig.offsetTimeout)
            .flatMapConcat {
              case (endpoints, offset) =>
                val serviceName = kafkaConfig.serviceName.map(name => s"[$name]").getOrElse("")
                log.debug("Kafka service {} located at URIs [{}] for producer of [{}]", serviceName, endpoints, topicId)
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
                  eventsPublisherFlow(endpoints, offset)

                // Return a Source[Future[Offset],_] where each produced element is a completed Offset.
                eventStreamSource // read from DB + userFlow
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
  private def eventualBrokersAndOffset(tagName: String): Future[(String, OffsetDao)] = {
    // TODO: review the OffsetStore API. I think `prepare()` does more than we need here. Ideally, prepare (create
    // schema and prepared statements) would be used when this actor starts and here we would only query for
    // the latest offset.
    val daoFuture: Future[OffsetDao] = offsetStore.prepare(s"topicProducer-$topicId", tagName)

    // null or empty strings become None, otherwise Some[String]
    def strToOpt(str: String) =
      Option(str).filter(_.trim.nonEmpty)

    // can be `None` if: not found using service locator (given serviceName)
    // and no fallback `brokers` are setup in `config`
    val serviceLookupFuture: Future[Option[String]] =
      kafkaConfig.serviceName match {
        case Some(name) =>
          locateService(name)
            .map { uris =>
              strToOpt(UriUtils.hostAndPorts(uris))
            }

        case None =>
          Future.successful(strToOpt(kafkaConfig.brokers))
      }

    val brokerList: Future[String] = serviceLookupFuture.map {
      case Some(brokers) => brokers
      case None =>
        kafkaConfig.serviceName match {
          case Some(serviceName) =>
            val msg = s"Unable to locate Kafka service named [$serviceName]. Retrying..."
            log.error(msg)
            throw new IllegalArgumentException(msg)
          case None =>
            val msg = "Unable to locate Kafka brokers URIs. Retrying..."
            log.error(msg)
            throw new RuntimeException(msg)
        }
    }

    brokerList.zip(daoFuture)
  }

  private def eventsPublisherFlow(endpoints: String, offsetDao: OffsetDao) =
    Flow.fromGraph(GraphDSL.create(kafkaFlowPublisher(endpoints)) { implicit builder => publishFlow =>
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

  private def kafkaFlowPublisher(endpoints: String): Flow[Message, _, _] = {
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
        ReactiveProducer.flexiFlow(producerSettings(endpoints))
      }
  }

  private def producerSettings(endpoints: String): ProducerSettings[String, Message] = {
    val keySerializer = new StringSerializer

    val baseSettings =
      ProducerSettings(context.system, keySerializer, serializer)
        .withProperty("client.id", self.path.toStringWithoutAddress)

    baseSettings.withBootstrapServers(endpoints)
  }
}
