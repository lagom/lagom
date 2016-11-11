package docs.home.scaladsl.persistence

import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.AggregateEventShards
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import com.lightbend.lagom.scaladsl.playjson.Serializers

object BlogEvent {
  val NumShards = 20
  // second param is optional, defaults to the class name
  val aggregateEventShards = AggregateEventTag.sharded[BlogEvent](NumShards)

  import play.api.libs.json._
  import Serializers.emptySingletonFormat

  val serializers = Vector(
    // FIXME Serializers(Json.format[PostAdded]),
    Serializers(Json.format[BodyChanged]),
    Serializers(Json.format[PostPublished]))
}

sealed trait BlogEvent extends AggregateEvent[BlogEvent] {
  override def aggregateTag: AggregateEventShards[BlogEvent] = BlogEvent.aggregateEventShards
}

final case class PostAdded(postId: String, content: PostContent) extends BlogEvent

final case class BodyChanged(postId: String, body: String) extends BlogEvent

final case class PostPublished(postId: String) extends BlogEvent
