/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import java.util.concurrent.atomic.AtomicInteger
import java.util.function.{ Function => JFunction }

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory

import com.lightbend.lagom.internal.broker.kafka.OffsetTracker.OffsetDao
import com.lightbend.lagom.internal.broker.kafka.serializer.KafkaSerializer
import com.lightbend.lagom.javadsl.api.Descriptor.Properties
import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.broker.kafka.Properties.PARTITION_KEY_STRATEGY
import com.lightbend.lagom.javadsl.broker.kafka.property.PartitionKeyStrategy
import com.lightbend.lagom.javadsl.persistence.Offset

import akka.Done
import akka.NotUsed
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.Status
import akka.actor.SupervisorStrategy
import akka.cluster.singleton.ClusterSingletonManager
import akka.cluster.singleton.ClusterSingletonManagerSettings
import akka.japi.Pair
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

/**
 * A Producer for publishing messages in Kafka using the akka-stream-kafka API.
 */
class Producer[Message] private (config: KafkaConfig, topicCall: TopicCall[Message], offsetDao: Future[OffsetDao], system: ActorSystem)(implicit mat: Materializer, ec: ExecutionContext) {

  private val log = LoggerFactory.getLogger(classOf[Producer[_]])

  private val producerConfig = ProducerConfig(system.settings.config)

  private lazy val producerId = Producer.KafkaClientIdSequenceNumber.getAndIncrement()

  // Note: The publish method doesn't return a CompletionStage[Done] because this method is not part of the public user api.
  def publish(readSideStream: JFunction[Offset, JSource[Pair[Message, Offset], NotUsed]]): Unit = {
    offsetDao.onComplete {
      case Success(offsetDao) =>
        val publisherProps = Producer.ProducerActor.props(config, topicCall, readSideStream, offsetDao)
        val backoffPublisherProps = BackoffSupervisor.propsWithSupervisorStrategy(
          publisherProps, s"KafkaProducerActor$producerId-${topicCall.topicId().value}", producerConfig.minBackoff, producerConfig.maxBackoff, producerConfig.randomBackoffFactor, SupervisorStrategy.stoppingStrategy
        )
        val publisherSingletonProps = ClusterSingletonManager.props(backoffPublisherProps, PoisonPill, ClusterSingletonManagerSettings(system))

        system.actorOf(publisherSingletonProps, s"KafkaProducerSingleton$producerId-${topicCall.topicId().value}")

      case Failure(err) =>
        log.error("Failed to create readside offset tracker instance. This is a framework issue, please report it.", err)
    }
  }
}

object Producer {
  private val KafkaClientIdSequenceNumber = new AtomicInteger(1)

  def apply[Message](config: KafkaConfig, topicCall: TopicCall[Message], system: ActorSystem, offsetDao: Future[OffsetDao])(implicit mat: Materializer, ec: ExecutionContext): Producer[Message] =
    new Producer(config, topicCall, offsetDao, system)

  private class ProducerActor[Message](kafkaConfig: KafkaConfig, topicCall: TopicCall[Message], readSideStream: JFunction[Offset, JSource[Pair[Message, Offset], NotUsed]], offsetDao: OffsetDao)(implicit mat: Materializer, ec: ExecutionContext) extends Actor with ActorLogging {

    /** Switch used to terminate the on-going Kafka publishing stream when this actor fails.*/
    private var shutdown: Option[KillSwitch] = None

    override def preStart(): Unit = {
      self ! ProducerActor.Start
    }

    override def postStop(): Unit = {
      shutdown.foreach(_.shutdown())
    }

    override def receive = {
      case ProducerActor.Start =>
        val readSideSource = readSideStream.apply(offsetDao.lastOffset).asScala.map(pair => pair.first -> pair.second)
        val (killSwitch, streamDone) = readSideSource
          .viaMat(KillSwitches.single)(Keep.right)
          .via(eventsPublisherFlow)
          .toMat(Sink.ignore)(Keep.both)
          .run()

        shutdown = Some(killSwitch)
        streamDone pipeTo self
      case Status.Failure(e) =>
        // The stream processing is resumed because the parent of this actor is a `BackoffSupervisor`. Refer to how this actor is created for details. 
        log.error(e, "Kafka producer stream for topic {} completed with an error. The stream processing will automatically resume.", topicCall.topicId())
        throw e

      case Done =>
        log.info("Kafka producer stream for topic {} was completed.", topicCall.topicId())
        context.stop(self) // FIXME: Do we need to stop the parents? (backoff and singleton cluster actors)
    }

    private def eventsPublisherFlow = Flow.fromGraph(GraphDSL.create(kafkaFlowPublisher) { implicit builder => publishFlow =>
      import GraphDSL.Implicits._
      val unzip = builder.add(Unzip[Message, Offset])
      val zip = builder.add(Zip[Any, Offset])
      val offsetCommitter = builder.add(Flow.fromFunction { e: (Any, Offset) => offsetDao.save(e._2) })

      unzip.out0 ~> publishFlow ~> zip.in0
      unzip.out1 ~> zip.in1
      zip.out ~> offsetCommitter.in
      FlowShape(unzip.in, offsetCommitter.out)
    })

    private def kafkaFlowPublisher: JFlow[Message, _, _] = {
      val property = PARTITION_KEY_STRATEGY.asInstanceOf[Properties.Property[PartitionKeyStrategy[Message]]]
      val strategy = topicCall.properties().getValueOf(property)
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
    }
  }

  private object ProducerActor {
    /** Start processing from the given offset. */
    case object Start

    def props[Message](kafkaConfig: KafkaConfig, topicCall: TopicCall[Message], readSideStream: JFunction[Offset, JSource[Pair[Message, Offset], NotUsed]], offsetDao: OffsetDao)(implicit mat: Materializer, ec: ExecutionContext) =
      Props(new ProducerActor[Message](kafkaConfig, topicCall, readSideStream, offsetDao))
  }
}
