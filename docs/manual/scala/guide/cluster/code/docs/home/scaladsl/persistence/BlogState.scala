package docs.home.scaladsl.persistence

//#full-example
import com.lightbend.lagom.scaladsl.playjson.Serializers

object BlogState {
  val empty = BlogState(None, published = false)

  //FIXME serialization, how to handle Option?
  val serializers = Vector.empty
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
