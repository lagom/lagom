/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package docs.home.scaladsl.serialization

import play.api.libs.json.{Format, Json}

//#complexMembers
case class UserMetadata(twitterHandle: String)
object UserMetadata {
  implicit val format: Format[UserMetadata] = Json.format
}
case class AddComment(userId: String, comment: String, userMetadata: UserMetadata)
object AddComment {
  implicit val format: Format[AddComment] = Json.format
}
//#complexMembers
