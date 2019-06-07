/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

//#full-example
import play.api.libs.json._

object BlogState {
  val empty = BlogState(None, published = false)

  implicit val postContentFormat = Json.format[PostContent]

  implicit val format: Format[BlogState] = Json.format[BlogState]
}

final case class BlogState(content: Option[PostContent], published: Boolean) {
  def withBody(body: String): BlogState = {
    content match {
      case Some(c) =>
        copy(content = Some(c.copy(body = body)))
      case None =>
        throw new IllegalStateException("Can't set body without content")
    }
  }

  def isEmpty: Boolean = content.isEmpty
}

final case class PostContent(title: String, body: String)
//#full-example

final case class PostSummary(postId: String, title: String)
