/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import java.util.concurrent.CompletionStage

import scala.compat.java8.FutureConverters._
import scala.util.{ Failure, Success }

import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory

import com.lightbend.lagom.internal.broker.kafka.serializer.KafkaDeserializer
import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall
import com.lightbend.lagom.javadsl.api.ServiceInfo
import com.lightbend.lagom.javadsl.api.broker.Subscriber

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.{ ConsumerMessage, ConsumerSettings, Subscriptions }
import akka.kafka.scaladsl.{ Consumer => ReactiveConsumer }
import akka.stream.{ FlowShape, Materializer }
import akka.stream.javadsl.{ Flow => JFlow, Source => JSource }
import akka.stream.scaladsl.{ Flow, GraphDSL, Sink, Unzip, Zip }

/**
 * A Consumer for consuming messages from Kafka using the akka-stream-kafka API.
 */
class Consumer[Message] private (config: KafkaConfig, topicCall: TopicCall[Message], groupId: Subscriber.GroupId, info: ServiceInfo, system: ActorSystem)(implicit mat: Materializer) extends Subscriber[Message] {

  private val log = LoggerFactory.getLogger(classOf[Consumer[_]])

  @throws(classOf[Subscriber.IllegalGroupId])
  override def withGroupId(groupId: String): Subscriber[Message] = {
    val newGroupId = {
      if (groupId == null) {
        // An empty group id is not allowed by Kafka (see https://issues.apache.org/jira/browse/KAFKA-2648
        // and https://github.com/akka/reactive-kafka/issues/155)
        val defaultGroupId = Consumer.GroupId.default(info)
        log.debug {
          "Passed a null groupId, but Kafka requires clients to set one (see KAFKA-2648). " +
            s"Defaulting $this consumer groupId to $defaultGroupId."
        }
        defaultGroupId
      } else Consumer.GroupId(groupId)
    }

    if (newGroupId.groupId == groupId) this
    else new Consumer(config, topicCall, newGroupId, info, system)
  }

  override def atMostOnceSource: JSource[Message, _] = {
    ReactiveConsumer.atMostOnceSource(consumerSettings, subscription)
      .map(_.value)
      .asJava
  }

  override def atLeastOnce(flow: JFlow[Message, Done, _]): CompletionStage[Done] = {
    // Creating a Source of pair where the first element is a reactive-kafka committable message, 
    // and the second it's the actual underlying message. Then, the source of pair is splitted into 
    // two streams, so that the `flow` passed in argument can be applied to the underlying message.
    // After having applied the `flow`, the two streams are combined back and the processed message's
    // offset is committed to Kafka.
    val pairedCommittableSource = ReactiveConsumer.committableSource(consumerSettings, subscription)
      .map(committableMessage => (committableMessage, committableMessage.record.value))

    val committOffsetFlow = Flow.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val unzip = builder.add(Unzip[ConsumerMessage.CommittableMessage[_, _], Message])
      val zip = builder.add(Zip[ConsumerMessage.CommittableMessage[_, _], Done])
      // parallelism set to 1 because offset should be committed in order
      val committer = Flow[(ConsumerMessage.CommittableMessage[_, _], Done)].mapAsync(parallelism = 1) {
        case (cm, _) =>
          implicit val ec = system.dispatcher
          cm.committableOffset.commitScaladsl() andThen {
            // logging is done here (instead than in a separated flow stage) because we want 
            // the log message to be displayed also when a failure occurs. Note that because
            // of the `mapAsync` semantic, the log message would NOT be displayed if done in
            // a separate, downstream, stage.
            case Success(_) =>
              log.info(s"Successfully committed offset ${cm.committableOffset.partitionOffset}.")
            case Failure(err: Throwable) =>
              log.warn(s"Failed to commit offset ${cm.committableOffset.partitionOffset}. Unless you have defined a custom supervision strategy, this stream is going to be completed with a failure.", err)
          }
      }

      unzip.out0 ~> zip.in0
      unzip.out1 ~> flow ~> zip.in1
      zip.out ~> committer

      FlowShape(unzip.in, committer.shape.out)
    })

    pairedCommittableSource.via(committOffsetFlow).runWith(Sink.ignore).toJava
  }

  private def consumerSettings = {
    val keyDeserializer = new StringDeserializer
    val valueDeserializer = {
      val messageSerializer = topicCall.messageSerializer()
      val protocol = messageSerializer.serializerForRequest().protocol()
      val deserializer = messageSerializer.deserializer(protocol)
      new KafkaDeserializer(deserializer)
    }

    ConsumerSettings(system, keyDeserializer, valueDeserializer)
      .withBootstrapServers(config.brokers)
      .withClientId(info.serviceName())
      .withGroupId(groupId.groupId())
  }

  private def subscription = Subscriptions.topics(topicCall.topicId().value)
}

object Consumer {
  def apply[Message](config: KafkaConfig, topicCall: TopicCall[Message], info: ServiceInfo, system: ActorSystem, mat: Materializer) =
    new Consumer(config, topicCall, GroupId.default(info), info, system)(mat)

  case class GroupId(groupId: String) extends Subscriber.GroupId {
    if (GroupId.validateGroupId(groupId))
      throw new Subscriber.IllegalGroupId(s"Failed to create group because [groupId=$groupId] contains invalid character(s). Check the Kafka spec for creating a valid group id.")
  }
  case object GroupId {
    private val InvalidGroupIdChars = Set('/', '\\', ',', '\u0000', ':', '"', '\'', ';', '*', '?', ' ', '\t', '\r', '\n', '=')
    // based on https://github.com/apache/kafka/blob/623ab1e7c6497c000bc9c9978637f20542a3191c/core/src/test/scala/unit/kafka/common/ConfigTest.scala#L60
    private def validateGroupId(groupId: String): Boolean = !groupId.exists { InvalidGroupIdChars.apply }

    def default(info: ServiceInfo): GroupId = GroupId(info.serviceName())
  }
}
