/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

import scala.collection.immutable

import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer

object BlogPostSerializerRegistry extends JsonSerializerRegistry {

  override def serializers: immutable.Seq[JsonSerializer[_]] =
    BlogCommand.serializers ++ BlogEvent.serializers :+ JsonSerializer[BlogState]

}
