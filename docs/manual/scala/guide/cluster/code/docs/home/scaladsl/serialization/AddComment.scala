/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package docs.home.scaladsl.serialization

import com.lightbend.lagom.scaladsl.playjson.Jsonable
import play.api.libs.json.{Format, Json}

//#complexMembers
case class UserMetadata(twitterHandle: String) extends Jsonable
object UserMetadata {
  implicit val format: Format[UserMetadata] = Json.format
}
case class AddComment(userId: String, comment: String, userMetadata: UserMetadata) extends Jsonable
object AddComment {
  implicit val format: Format[AddComment] = Json.format
}
//#complexMembers
