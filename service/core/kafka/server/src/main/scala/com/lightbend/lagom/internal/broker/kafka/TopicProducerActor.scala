/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.broker.kafka

import java.net.URI

import akka.Done
import akka.NotUsed
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.Status
import akka.kafka.ProducerMessage
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.{ Producer => ReactiveProducer }
import akka.pattern.pipe
import akka.persistence.query.{ Offset => AkkaOffset }
import akka.stream.FlowShape
import akka.stream.KillSwitch
import akka.stream.KillSwitches
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Merge
import akka.stream.scaladsl.Partition
import akka.stream.scaladsl.RestartSource
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.lightbend.lagom.internal.api.UriUtils
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
      kafkaConfig: KafkaConfig,
      producerConfig: ProducerConfig,
      locateService: String => Future[Seq[URI]],
      topicId: String,
      eventStreamFactory: (String, AkkaOffset) => Source[InternalTopicProducerCommand[Message], _],
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
    eventStreamFactory: (String, AkkaOffset) => Source[InternalTopicProducerCommand[Message], _],
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
                val eventStreamSource: Source[InternalTopicProducerCommand[Message], _] =
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

                val eventPublisherFlow = eventsPublisherFlow(endpoints, offset)

                // Return a Source[Future[Offset],_] where each produced element is a completed Offset.
                eventStreamSource // read from DB + userFlow
                  .map(command =>
                    command.withOffset(
                      ProjectionSpi
                        .afterUserFlow(workerCoordinates.projectionName, workerCoordinates.tagName, command.offset)
                    )
                  )
                  .via(eventPublisherFlow) //  Kafka write + offset commit
                  .map(
                    _.map(offset =>
                      ProjectionSpi
                        .completedProcessing(workerCoordinates.projectionName, workerCoordinates.tagName, offset)
                    )
                  )
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

  private def eventsPublisherFlow(
      endpoints: String,
      offsetDao: OffsetDao
  ): Flow[InternalTopicProducerCommand[Message], Future[AkkaOffset], Any] =
    Flow.fromGraph(GraphDSL.create(kafkaFlowPublisher(endpoints)) { implicit builder => publishFlow =>
      import GraphDSL.Implicits._

      val partition = builder.add(Partition[InternalTopicProducerCommand[Message]](2, {
        case _: InternalTopicProducerCommand.EmitAndCommit[Message] => 0
        case _: InternalTopicProducerCommand.Commit[Message]        => 1
      }))
      val merge = builder.add(Merge[InternalTopicProducerCommand[Message]](2))

      val offsetCommitter = builder.add(Flow.fromFunction { command: InternalTopicProducerCommand[Message] =>
        offsetDao.saveOffset(command.offset).map(_ => command.offset)
      })

      partition.out(0) ~> publishFlow ~> merge.in(0)
      partition.out(1) ~> merge.in(1)
      merge.out ~> offsetCommitter.in

      FlowShape(partition.in, offsetCommitter.out)
    })

  private def kafkaFlowPublisher(
      endpoints: String
  ): Flow[InternalTopicProducerCommand[Message], InternalTopicProducerCommand[Message], _] = {
    def keyOf(message: Message): String = {
      partitionKeyStrategy match {
        case Some(strategy) => strategy(message)
        case None           => null
      }
    }

    Flow[InternalTopicProducerCommand[Message]]
      .flatMapConcat {
        case command @ InternalTopicProducerCommand.EmitAndCommit(message, _) =>
          val producerMessage = ProducerMessage
            .Message(new ProducerRecord[String, Message](topicId, keyOf(message), message), NotUsed)

          Source
            .single(producerMessage)
            .via(ReactiveProducer.flexiFlow(producerSettings(endpoints)))
            .map(_ => command)
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
