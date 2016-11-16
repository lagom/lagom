/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import scala.concurrent.ExecutionContext
import scala.util.Failure
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import com.lightbend.lagom.internal.broker.kafka.serializer.KafkaSerializer
import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import akka.Done
import akka.NotUsed
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.SupervisorStrategy
import akka.cluster.sharding.ClusterShardingSettings
import akka.kafka.ProducerMessage
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.{ Producer => ReactiveProducer }
import akka.pattern.BackoffSupervisor
import akka.pattern.pipe
import akka.stream.FlowShape
import akka.stream.KillSwitch
import akka.stream.KillSwitches
import akka.stream.Materializer
import akka.stream.javadsl.{ Flow => JFlow }
import akka.stream.javadsl.{ Source => JSource }
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Unzip
import akka.stream.scaladsl.Zip
import com.lightbend.lagom.internal.broker.TaggedOffsetTopicProducer
import com.lightbend.lagom.internal.javadsl.persistence.OffsetAdapter
import com.lightbend.lagom.internal.persistence.cluster.{ ClusterDistribution, ClusterDistributionSettings }
import com.lightbend.lagom.internal.persistence.{ OffsetDao, OffsetStore }
import com.lightbend.lagom.internal.persistence.cluster.ClusterDistribution.EnsureActive
import com.lightbend.lagom.javadsl.api.broker.kafka.KafkaProperties
import com.lightbend.lagom.javadsl.persistence.{ AggregateEvent, AggregateEventTag, Offset }

import scala.collection.JavaConverters._

/**
 * A Producer for publishing messages in Kafka using the akka-stream-kafka API.
 */
class Producer[Message] private (config: KafkaConfig, topicCall: TopicCall[Message], system: ActorSystem)(implicit mat: Materializer, ec: ExecutionContext) {

  private val producerConfig = ProducerConfig(system.settings.config)

  def publishTaggedOffsetProducer[E <: AggregateEvent[E]](
    producer:    TaggedOffsetTopicProducer[Message, E],
    offsetStore: OffsetStore
  ): Unit = {
    val clazz: Class[E] = producer.tags.get(0).eventType
    val readSideStream = (tag: AggregateEventTag[E], offset: Offset) => producer.readSideStream(tag, offset)
    val publisherProps = Producer.ProducerActor.props(config, topicCall, readSideStream, clazz, offsetStore)
    val backoffPublisherProps = BackoffSupervisor.propsWithSupervisorStrategy(
      publisherProps, s"producer", producerConfig.minBackoff, producerConfig.maxBackoff,
      producerConfig.randomBackoffFactor, SupervisorStrategy.stoppingStrategy
    )
    val clusterShardingSettings = ClusterShardingSettings(system).withRole(producerConfig.role)

    ClusterDistribution(system).start(
      s"kafkaProducer-${topicCall.topicId.value}",
      backoffPublisherProps,
      producer.tags.asScala.map(_.tag).toSet,
      ClusterDistributionSettings(system).copy(clusterShardingSettings = clusterShardingSettings)
    )
  }
}

object Producer {
  def apply[Message](
    config:    KafkaConfig,
    topicCall: TopicCall[Message], system: ActorSystem
  )(implicit mat: Materializer, ec: ExecutionContext): Producer[Message] =
    new Producer(config, topicCall, system)

  private class TaggedOffsetProducerActor[Message, Event <: AggregateEvent[Event]](
    kafkaConfig:        KafkaConfig,
    topicCall:          TopicCall[Message],
    eventStreamFactory: (AggregateEventTag[Event], Offset) => JSource[akka.japi.Pair[Message, Offset], _],
    clazz:              Class[Event],
    offsetStore:        OffsetStore
  )(implicit mat: Materializer, ec: ExecutionContext) extends Actor with ActorLogging {

    /** Switch used to terminate the on-going Kafka publishing stream when this actor fails.*/
    private var shutdown: Option[KillSwitch] = None

    override def postStop(): Unit = {
      shutdown.foreach(_.shutdown())
    }

    override def receive = {
      case EnsureActive(tagName) =>
        val tag = AggregateEventTag.of(clazz, tagName)

        offsetStore.prepare(s"topicProducer-${topicCall.topicId.value}", tagName) pipeTo self
        context.become(preparing(tag))
    }

    private def preparing(tag: AggregateEventTag[Event]): Receive = {
      case dao: OffsetDao =>
        val readSideSource = eventStreamFactory(tag, OffsetAdapter.offsetToDslOffset(dao.loadedOffset)).asScala
          .map(pair => pair.first -> pair.second)

        val (killSwitch, streamDone) = readSideSource
          .viaMat(KillSwitches.single)(Keep.right)
          .via(eventsPublisherFlow(dao))
          .toMat(Sink.ignore)(Keep.both)
          .run()

        shutdown = Some(killSwitch)
        streamDone pipeTo self
        context.become(active)

      case Failure(e) =>
        throw e

      case EnsureActive(tagName) =>
      // We are preparing
    }

    private def active: Receive = {
      case Failure(e) =>
        throw e

      case Done =>
        log.info("Kafka producer stream for topic {} was completed.", topicCall.topicId())
        context.stop(self)

      case EnsureActive(tagName) =>
      // Already active
    }

    private def eventsPublisherFlow(offsetDao: OffsetDao) = Flow.fromGraph(GraphDSL.create(kafkaFlowPublisher) { implicit builder => publishFlow =>
      import GraphDSL.Implicits._
      val unzip = builder.add(Unzip[Message, Offset])
      val zip = builder.add(Zip[Any, Offset])
      val offsetCommitter = builder.add(Flow.fromFunction { e: (Any, Offset) =>
        offsetDao.saveOffset(OffsetAdapter.dslOffsetToOffset(e._2))
      })

      unzip.out0 ~> publishFlow ~> zip.in0
      unzip.out1 ~> zip.in1
      zip.out ~> offsetCommitter.in
      FlowShape(unzip.in, offsetCommitter.out)
    })

    private def kafkaFlowPublisher: JFlow[Message, _, _] = {
      val strategy = topicCall.properties().getValueOf(KafkaProperties.partitionKeyStrategy())
      def keyOf(message: Message): String = {
        if (strategy == null) null
        else strategy.computePartitionKey(message)
      }

      (Flow.fromFunction { (message: Message) =>
        ProducerMessage.Message(new ProducerRecord[String, Message](topicCall.topicId.value, keyOf(message), message), NotUsed)
      } via {
        ReactiveProducer.flow(producerSettings)
      }).asJava
    }

    private def producerSettings: ProducerSettings[String, Message] = {
      val keySerializer = new StringSerializer
      val valueSerializer = new KafkaSerializer(topicCall.messageSerializer().serializerForRequest())

      ProducerSettings(context.system, keySerializer, valueSerializer)
        .withBootstrapServers(kafkaConfig.brokers)
        .withProperty("client.id", self.path.toStringWithoutAddress)
    }
  }

  private object ProducerActor {
    def props[Message, Event <: AggregateEvent[Event]](
      kafkaConfig:        KafkaConfig,
      topicCall:          TopicCall[Message],
      eventStreamFactory: (AggregateEventTag[Event], Offset) => JSource[akka.japi.Pair[Message, Offset], _],
      clazz:              Class[Event],
      offsetStore:        OffsetStore
    )(implicit mat: Materializer, ec: ExecutionContext) =
      Props(new TaggedOffsetProducerActor[Message, Event](kafkaConfig, topicCall, eventStreamFactory, clazz, offsetStore))
  }
}
