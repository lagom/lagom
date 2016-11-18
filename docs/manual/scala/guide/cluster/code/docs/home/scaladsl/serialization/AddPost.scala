package docs.home.scaladsl.serialization

import com.lightbend.lagom.scaladsl.playjson.Jsonable
import play.api.libs.json.{Format, Json}

//#markerTrait
case class AddPost(text: String) extends Jsonable
//#markerTrait

object AddPost {

  //#format
  val format: Format[AddPost] = Json.format[AddPost]
  //#format

}