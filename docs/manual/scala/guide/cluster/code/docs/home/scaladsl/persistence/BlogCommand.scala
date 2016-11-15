package docs.home.scaladsl.persistence

//#full-example
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import akka.Done
import com.lightbend.lagom.scaladsl.playjson.Serializers

sealed trait BlogCommand

object BlogCommand {
  import play.api.libs.json._
  import Serializers.emptySingletonFormat

  val serializers = Vector(
    // FIXME Serializers(Json.format[AddPost]),
    Serializers(Json.format[AddPostDone]),
    Serializers(emptySingletonFormat(GetPost)),
    Serializers(Json.format[ChangeBody]),
    Serializers(emptySingletonFormat(Publish)))
}

//#AddPost
final case class AddPost(content: PostContent) extends BlogCommand with ReplyType[AddPostDone]
//#AddPost

final case class AddPostDone(postId: String)

case object GetPost extends BlogCommand with ReplyType[PostContent]

final case class ChangeBody(body: String) extends BlogCommand with ReplyType[Done]

case object Publish extends BlogCommand with ReplyType[Done]
//#full-example
