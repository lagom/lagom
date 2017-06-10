package docs.home.scaladsl.serialization

import com.lightbend.lagom.scaladsl.playjson.Jsonable
import play.api.libs.json.{Format, Json}

case class AddPost(text: String) extends Jsonable

object AddPost {

  //#format
  implicit val format: Format[AddPost] = Json.format
  //#format

}