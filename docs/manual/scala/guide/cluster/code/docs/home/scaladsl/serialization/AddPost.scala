package docs.home.scaladsl.serialization

import play.api.libs.json.{Format, Json}

case class AddPost(text: String)

object AddPost {

  //#format
  implicit val format: Format[AddPost] = Json.format
  //#format

}