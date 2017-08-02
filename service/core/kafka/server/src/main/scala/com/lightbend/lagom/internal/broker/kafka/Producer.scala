/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import java.net.URI

import scala.concurrent.{ ExecutionContext, Future }
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ Serializer, StringSerializer }
import akka.Done
import akka.NotUsed
import akka.actor.{ Actor, ActorLogging, ActorSystem, Props, Status, SupervisorStrategy }
import akka.cluster.sharding.ClusterShardingSettings
import akka.kafka.ProducerMessage
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.{ Producer => ReactiveProducer }
import akka.pattern.BackoffSupervisor
import akka.pattern.pipe
import akka.persistence.query.Offset
import akka.stream.FlowShape
import akka.stream.KillSwitch
import akka.stream.KillSwitches
import akka.stream.Materializer
import akka.stream.scaladsl._
import com.lightbend.lagom.internal.api.UriUtils
import com.lightbend.lagom.internal.persistence.cluster.{ ClusterDistribution, ClusterDistributionSettings }
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.spi.persistence.{ OffsetDao, OffsetStore }

import scala.collection.immutable

/**
 * A Producer for publishing messages in Kafka using the akka-stream-kafka API.
 */
private[lagom] object Producer {

  def startTaggedOffsetProducer[Message](
    system:               ActorSystem,
    tags:                 immutable.Seq[String],
    kafkaConfig:          KafkaConfig,
    locateService:        String => Future[Seq[URI]],
    topicId:              String,
    eventStreamFactory:   (String, Offset) => Source[(Message, Offset), _],
    partitionKeyStrategy: Option[Message => String],
    serializer:           Serializer[Message],
    offsetStore:          OffsetStore
  )(implicit mat: Materializer, ec: ExecutionContext): Unit = {

    val producerConfig = ProducerConfig(system.settings.config)
    val publisherProps = TaggedOffsetProducerActor.props(kafkaConfig, locateService, topicId, eventStreamFactory,
      partitionKeyStrategy, serializer, offsetStore)

    val backoffPublisherProps = BackoffSupervisor.propsWithSupervisorStrategy(
      publisherProps, s"producer", producerConfig.minBackoff, producerConfig.maxBackoff,
      producerConfig.randomBackoffFactor, SupervisorStrategy.stoppingStrategy
    )
    val clusterShardingSettings = ClusterShardingSettings(system).withRole(producerConfig.role)

    ClusterDistribution(system).start(
      s"kafkaProducer-$topicId",
      backoffPublisherProps,
      tags.toSet,
      ClusterDistributionSettings(system).copy(clusterShardingSettings = clusterShardingSettings)
    )
  }

  private class TaggedOffsetProducerActor[Message](
    kafkaConfig:          KafkaConfig,
    locateService:        String => Future[Seq[URI]],
    topicId:              String,
    eventStreamFactory:   (String, Offset) => Source[(Message, Offset), _],
    partitionKeyStrategy: Option[Message => String],
    serializer:           Serializer[Message],
    offsetStore:          OffsetStore
  )(implicit mat: Materializer, ec: ExecutionContext) extends Actor with ActorLogging {

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
                .map { uris => strToOpt(UriUtils.hostAndPorts(uris)) }

            case None =>
              Future.successful(strToOpt(kafkaConfig.brokers))
          }

        serviceLookupFuture.zip(daoFuture) pipeTo self

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
      streamDone pipeTo self
      context.become(active)
    }

    private def eventsPublisherFlow(endpoints: String, offsetDao: OffsetDao) =
      Flow.fromGraph(GraphDSL.create(kafkaFlowPublisher(endpoints)) { implicit builder => publishFlow =>
        import GraphDSL.Implicits._
        val unzip = builder.add(Unzip[Message, Offset])
        val zip = builder.add(Zip[Any, Offset])
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

      Flow[Message].map { message =>
        ProducerMessage.Message(new ProducerRecord[String, Message](topicId, keyOf(message), message), NotUsed)
      } via {
        ReactiveProducer.flow(producerSettings(endpoints))
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

  private object TaggedOffsetProducerActor {
    def props[Message](
      kafkaConfig:          KafkaConfig,
      locateService:        String => Future[Seq[URI]],
      topicId:              String,
      eventStreamFactory:   (String, Offset) => Source[(Message, Offset), _],
      partitionKeyStrategy: Option[Message => String],
      serializer:           Serializer[Message],
      offsetStore:          OffsetStore
    )(implicit mat: Materializer, ec: ExecutionContext) =
      Props(new TaggedOffsetProducerActor[Message](kafkaConfig, locateService, topicId, eventStreamFactory,
        partitionKeyStrategy, serializer, offsetStore))
  }
}
