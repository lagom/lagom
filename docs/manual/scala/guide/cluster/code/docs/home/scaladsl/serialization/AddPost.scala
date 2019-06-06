/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.serialization

import play.api.libs.json.Format
import play.api.libs.json.Json

case class AddPost(text: String)

object AddPost {

  //#format
  implicit val format: Format[AddPost] = Json.format
  //#format

}
