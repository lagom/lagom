/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.serialization

import play.api.libs.json.Format
import play.api.libs.json.Json

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
