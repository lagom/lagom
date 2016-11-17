package docs.home.scaladsl.persistence

//#full-example
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.AggregateEventShards
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import com.lightbend.lagom.scaladsl.playjson.{Jsonable, Serializers}

object BlogEvent {
  val NumShards = 20
  // second param is optional, defaults to the class name
  val Tag = AggregateEventTag.sharded[BlogEvent](NumShards)

  import play.api.libs.json._
  private implicit val postContentFormat = Json.format[PostContent]

  val serializers = Vector(
    Serializers(Json.format[PostAdded]),
    Serializers(Json.format[BodyChanged]),
    Serializers(Json.format[PostPublished]))
}

sealed trait BlogEvent extends AggregateEvent[BlogEvent] with Jsonable {
  override def aggregateTag: AggregateEventShards[BlogEvent] = BlogEvent.Tag
}

final case class PostAdded(postId: String, content: PostContent) extends BlogEvent

final case class BodyChanged(postId: String, body: String) extends BlogEvent

final case class PostPublished(postId: String) extends BlogEvent
//#full-example
