/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
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
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import org.apache.kafka.clients.producer.ProducerRecord
import akka.persistence.query.Offset
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

private[lagom] class TaggedOffsetProducerActor[Message](
                                                  kafkaConfig: KafkaConfig,
                                                  locateService: String => Future[Seq[URI]],
                                                  topicId: String,
                                                  eventStreamFactory: (String, Offset) => Source[(Message, Offset), _],
                                                  partitionKeyStrategy: Option[Message => String],
                                                  serializer: Serializer[Message],
                                                  offsetStore: OffsetStore
                                                )(implicit mat: Materializer, ec: ExecutionContext)
  extends Actor
    with ActorLogging {

  /** Switch used to terminate the on-going Kafka publishing stream when this actor fails.*/
  private var shutdown: Option[KillSwitch] = None

  override def postStop(): Unit = {
    shutdown.foreach(_.shutdown())
  }

  override def receive = {
    case EnsureActive(tag) =>
      val daoFuture = offsetStore.prepare(s"topicProducer-$topicId", tag)

      // null or empty strings become None, otherwise Some[String]
      def strToOpt(str: String) =
        Option(str).filter(_.trim.nonEmpty)

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

      serviceLookupFuture.zip(daoFuture).pipeTo(self)

      context.become(initializing(tag))
  }

  def generalHandler: Receive = {
    case Status.Failure(e) => throw e
    case EnsureActive(_)   =>
  }

  private def initializing(tag: String): Receive = generalHandler.orElse {

    case (Some(endpoints: String), dao: OffsetDao) =>
      // log is impacted by kafkaConfig.serviceName
      val serviceName = kafkaConfig.serviceName.map(name => s"[$name]").getOrElse("")
      log.debug("Kafka service {} located at URIs [{}] for producer of [{}]", serviceName, endpoints, topicId)
      run(tag, endpoints, dao)

    case (None, _) =>
      // log is impacted by kafkaConfig.serviceName
      kafkaConfig.serviceName match {
        case Some(serviceName) => log.error("Unable to locate Kafka service named [{}]", serviceName)
        case None              => log.error("Unable to locate Kafka brokers URIs")
      }

      context.stop(self)
  }

  private def active: Receive = generalHandler.orElse {
    case Done =>
      log.info("Kafka producer stream for topic {} was completed.", topicId)
      context.stop(self)
  }

  private def run(tag: String, endpoints: String, dao: OffsetDao) = {
    val readSideSource = eventStreamFactory(tag, dao.loadedOffset)

    val (killSwitch, streamDone) = readSideSource
      .viaMat(KillSwitches.single)(Keep.right)
      .via(eventsPublisherFlow(endpoints, dao))
      .toMat(Sink.ignore)(Keep.both)
      .run()

    shutdown = Some(killSwitch)
    streamDone.pipeTo(self)
    context.become(active)
  }

  private def eventsPublisherFlow(endpoints: String, offsetDao: OffsetDao) =
    Flow.fromGraph(GraphDSL.create(kafkaFlowPublisher(endpoints)) { implicit builder => publishFlow =>
      import GraphDSL.Implicits._
      val unzip = builder.add(Unzip[Message, Offset])
      val zip   = builder.add(Zip[Any, Offset])
      val offsetCommitter = builder.add(Flow.fromFunction { e: (Any, Offset) =>
        offsetDao.saveOffset(e._2)
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

private[lagom] object TaggedOffsetProducerActor {
  def props[Message](
                      kafkaConfig: KafkaConfig,
                      locateService: String => Future[Seq[URI]],
                      topicId: String,
                      eventStreamFactory: (String, Offset) => Source[(Message, Offset), _],
                      partitionKeyStrategy: Option[Message => String],
                      serializer: Serializer[Message],
                      offsetStore: OffsetStore
                    )(implicit mat: Materializer, ec: ExecutionContext) =
    Props(
      new TaggedOffsetProducerActor[Message](
        kafkaConfig,
        locateService,
        topicId,
        eventStreamFactory,
        partitionKeyStrategy,
        serializer,
        offsetStore
      )
    )
}