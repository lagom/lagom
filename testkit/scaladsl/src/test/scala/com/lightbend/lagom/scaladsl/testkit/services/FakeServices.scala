/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.testkit.services

import java.util.concurrent.ConcurrentLinkedQueue

import akka.Done
import akka.NotUsed
import akka.stream.scaladsl.Flow
import com.lightbend.lagom.internal.api.broker.MessageMetadataKey
import com.lightbend.lagom.internal.broker.kafka.KafkaMetadataKeys
import com.lightbend.lagom.scaladsl.api._
import com.lightbend.lagom.scaladsl.api.Service._
import com.lightbend.lagom.scaladsl.api.broker.Message
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.server.LagomApplication
import com.lightbend.lagom.scaladsl.server.LagomApplicationContext
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.typesafe.config.ConfigFactory
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import play.api.libs.ws.ahc.AhcWSComponents

import scala.collection.immutable.Seq
import scala.concurrent.Future

object AlphaService {
  val TOPIC_ID               = "alpha-topic-name"
  val TOPIC_WITH_METADATA_ID = "alpha-topic-with-metadata-name"
}

trait AlphaService extends Service {
  import AlphaService._

  override def descriptor: Descriptor = {
    named("alpha")
      .addTopics(
        topic(TOPIC_ID, messages),
        topic(TOPIC_WITH_METADATA_ID, messagesWithMetadata)
      )
  }

  def messages: Topic[AlphaEvent]

  def messagesWithMetadata: Topic[AlphaEvent]
}

case class AlphaEvent(message: Int)

object AlphaEvent {
  import play.api.libs.json._

  implicit val format: Format[AlphaEvent] = Json.format[AlphaEvent]

  def withMetadata(number: Int): Message[AlphaEvent] = {
    val event = AlphaEvent(number)
    val headers = new RecordHeaders()
      .add("header-one", s"ho-$number".getBytes)
      .add("header-two", s"ht-$number".getBytes)
    Message(event)
      .add(MessageMetadataKey.messageKey[String] -> s"key-$number")
      .add(KafkaMetadataKeys.Offset -> 10L * number)
      .add(KafkaMetadataKeys.Partition -> 100 * number)
      .add(KafkaMetadataKeys.Topic -> s"topic-$number")
      .add(KafkaMetadataKeys.Headers -> headers)
      .add(KafkaMetadataKeys.Timestamp -> 1000L * number)
      .add(KafkaMetadataKeys.TimestampType -> TimestampType.LOG_APPEND_TIME)
  }
}

// ------------------------------------------------------
object FakesSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] =
    List(JsonSerializer[AlphaEvent], JsonSerializer[ReceivedMessage])
}

// ------------------------------------------------------

abstract class DownstreamApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
    with CassandraPersistenceComponents
    with ProvidesAdditionalConfiguration
    with AhcWSComponents {
  // This is a hack so C* persistence in this Applicaiton doesn't complain. C* Persistence is only used
  // so intances of this Application can mix-in a TopicComponents implementation (Test or Kafka)
  override def additionalConfiguration: AdditionalConfiguration = {
    import scala.collection.JavaConverters._
    super.additionalConfiguration ++ ConfigFactory.parseMap(
      Map(
        "cassandra-journal.keyspace"                     -> "asdf",
        "cassandra-snapshot-store.keyspace"              -> "asdf",
        "lagom.persistence.read-side.cassandra.keyspace" -> "asdf"
      ).asJava
    )
  }

  override lazy val jsonSerializerRegistry: FakesSerializerRegistry.type =
    FakesSerializerRegistry

  lazy val alphaService = serviceClient.implement[AlphaService]

  override lazy val lagomServer =
    serverFor[CharlieService](new CharlieServiceImpl(alphaService))
}

trait CharlieService extends Service {
  override def descriptor: Descriptor = {
    named("beta")
      .addCalls(
        namedCall("messages", messages)
      )
      .addTopics(
        topic("charlie-topic", topicCall)
      )
  }

  def messages: ServiceCall[NotUsed, Seq[ReceivedMessage]]
  def topicCall: Topic[String]
}

case class ReceivedMessage(topicId: String, msg: Int)

object ReceivedMessage {
  import play.api.libs.json._

  implicit val format: Format[ReceivedMessage] = Json.format[ReceivedMessage]
}

class CharlieServiceImpl(alpha: AlphaService) extends CharlieService {
  private val receivedMessages = new ConcurrentLinkedQueue[ReceivedMessage]

  alpha.messages.subscribe.atLeastOnce(
    Flow.fromFunction[AlphaEvent, Done](ae => {
      receivedMessages.add(ReceivedMessage("A", ae.message))
      akka.Done
    })
  )

  override def messages: ServiceCall[NotUsed, Seq[ReceivedMessage]] =
    ServiceCall { _ =>
      Future.successful {
        import collection.JavaConverters._
        Seq(receivedMessages.asScala.toSeq: _*)
      }
    }

  override def topicCall: Topic[String] = ???
}
