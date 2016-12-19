package docs.mb


import akka.util.ByteString
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.broker.kafka.{KafkaProperties, PartitionKeyStrategy}
import com.lightbend.lagom.scaladsl.api.deser.MessageSerializer
import com.lightbend.lagom.scaladsl.api.deser.MessageSerializer.{NegotiatedDeserializer, NegotiatedSerializer}
import com.lightbend.lagom.scaladsl.api.transport._
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service}
import play.api.libs.json.Json

import scala.collection.immutable.Seq
import scala.util.control.NonFatal

/**
  *
  */
trait BlogPostService extends Service {


  override final def descriptor: Descriptor = {
    import Service._

    //#publishing
    implicit val blogPostEventMessageSerializer = new serialization.BlogPostEventMessageSerializer

    named("blogpostservice")
      .withTopics(
        topic("blogposts", blogPostEvents) // uses blogPostEventMessageSerializer implicitly
          .addProperty(
            KafkaProperties.partitionKeyStrategy,
            PartitionKeyStrategy[BlogPostEvent](_.postId)
          )
      )
    //#publishing
  }

  def blogPostEvents: Topic[BlogPostEvent]
}

//#content
sealed trait BlogPostEvent {
  def postId: String
}

case class BlogPostCreated(postId: String, title: String) extends BlogPostEvent

case class BlogPostPublished(postId: String) extends BlogPostEvent
//#content


object serialization {

  //#content-formatters
  implicit val blogPostCreatedFormat = Json.format[BlogPostCreated]
  implicit val blogPostPublishedFormat = Json.format[BlogPostPublished]
  //#content-formatters

  //#polymorphic-play-json

  // Create a custom MessageSerializer you will use on the Service Descriptor.
  class BlogPostEventMessageSerializer extends MessageSerializer[BlogPostEvent, ByteString] {

    override def acceptResponseProtocols: Seq[MessageProtocol] =
      List(MessageProtocol(Some("application/json"), None, None))

    private val deserializer = new BlogPostEventDeserializer
    private val serializer = new BlogPostEventSerializer

    override def serializerForRequest = serializer
    override def deserializer(protocol: MessageProtocol) =deserializer
    override def serializerForResponse(acceptedMessageProtocols: Seq[MessageProtocol]) = serializer

  }

  class BlogPostEventDeserializer extends NegotiatedDeserializer[BlogPostEvent, ByteString] {

    def deserialize(bytes: ByteString) = {
      try {
        val jsValue = Json.parse(bytes.iterator.asInputStream)
        (jsValue \ "event_type").as[String] match {
          case "postCreated" => jsValue.as[BlogPostCreated]
          case "postPublished" => jsValue.as[BlogPostPublished]
          case x => throw DeserializationException(s"Unknown event type: $x ")
        }
      } catch {
        case NonFatal(e) => throw DeserializationException(e)
      }
    }
  }

  class BlogPostEventSerializer extends NegotiatedSerializer[BlogPostEvent, ByteString] {
    @scala.throws[SerializationException]
    override def serialize(message: BlogPostEvent): ByteString = {
      val (jsValue, eventType) =
        message match {
          case m: BlogPostCreated => (Json.toJson(m), "postCreated")
          case m: BlogPostPublished => (Json.toJson(m), "postPublished")
        }
      import play.api.libs.json._
      val transformer = __.json.update((__ \ 'event_type).json.put(JsString(eventType)))
      ByteString.fromString(Json.stringify(jsValue.transform(transformer).get))
    }
  }

  //#polymorphic-play-json

}

