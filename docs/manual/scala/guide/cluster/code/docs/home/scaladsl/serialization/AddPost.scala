/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.serialization

import play.api.libs.json.Format
import play.api.libs.json.Json

case class AddPost(text: String)

object AddPost {
  implicit val format: Format[AddPost] = Json.format
}
