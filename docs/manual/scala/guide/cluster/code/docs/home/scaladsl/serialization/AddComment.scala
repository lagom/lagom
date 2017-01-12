/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package docs.home.scaladsl.serialization

import com.lightbend.lagom.scaladsl.playjson.Jsonable
import play.api.libs.json.Json

object AddComment {

  //#complexMembers
  case class UserMetadata(twitterHandle: String)
  case class AddComment(userId: String, comment: String, userMetadata: UserMetadata) extends Jsonable

  implicit val metadataFormat = Json.format[UserMetadata]
  val format = Json.format[AddComment]
  //#complexMembers

}
