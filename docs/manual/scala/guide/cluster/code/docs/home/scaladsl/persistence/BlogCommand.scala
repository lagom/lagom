/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

//#full-example
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import akka.Done
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer

sealed trait BlogCommand

object BlogCommand {
  import play.api.libs.json._
  import JsonSerializer.emptySingletonFormat

  implicit val postContentFormat = Json.format[PostContent]

  val serializers = Vector(
    JsonSerializer(Json.format[AddPost]),
    JsonSerializer(Json.format[AddPostDone]),
    JsonSerializer(emptySingletonFormat(GetPost)),
    JsonSerializer(Json.format[ChangeBody]),
    JsonSerializer(emptySingletonFormat(Publish))
  )
}

//#AddPost
final case class AddPost(content: PostContent) extends BlogCommand with ReplyType[AddPostDone]
//#AddPost

final case class AddPostDone(postId: String)

case object GetPost extends BlogCommand with ReplyType[PostContent]

final case class ChangeBody(body: String) extends BlogCommand with ReplyType[Done]

case object Publish extends BlogCommand with ReplyType[Done]
//#full-example
