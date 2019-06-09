/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.mb

import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.broker.kafka.KafkaProperties
import com.lightbend.lagom.scaladsl.api.broker.kafka.PartitionKeyStrategy
import com.lightbend.lagom.scaladsl.api.Descriptor
import com.lightbend.lagom.scaladsl.api.Service
import play.api.libs.json._

import scala.collection.immutable.Seq

/**
 *
 */
trait BlogPostService extends Service {

  final override def descriptor: Descriptor = {
    import Service._

    //#withTopics
    named("blogpostservice")
      .withTopics(
        topic("blogposts", blogPostEvents)
          .addProperty(
            KafkaProperties.partitionKeyStrategy,
            PartitionKeyStrategy[BlogPostEvent](_.postId)
          )
      )
    //#withTopics
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

//#content-formatters
case object BlogPostCreated {
  implicit val blogPostCreatedFormat: Format[BlogPostCreated] = Json.format
}

case object BlogPostPublished {
  implicit val blogPostPublishedFormat: Format[BlogPostPublished] = Json.format
}
//#content-formatters

//#polymorphic-play-json
object BlogPostEvent {
  implicit val reads: Reads[BlogPostEvent] = {
    (__ \ "event_type").read[String].flatMap {
      case "postCreated"   => implicitly[Reads[BlogPostCreated]].map(identity)
      case "postPublished" => implicitly[Reads[BlogPostPublished]].map(identity)
      case other           => Reads(_ => JsError(s"Unknown event type $other"))
    }
  }
  implicit val writes: Writes[BlogPostEvent] = Writes { event =>
    val (jsValue, eventType) = event match {
      case m: BlogPostCreated   => (Json.toJson(m)(BlogPostCreated.blogPostCreatedFormat), "postCreated")
      case m: BlogPostPublished => (Json.toJson(m)(BlogPostPublished.blogPostPublishedFormat), "postPublished")
    }
    jsValue.transform(__.json.update((__ \ 'event_type).json.put(JsString(eventType)))).get
  }
}

//#polymorphic-play-json
