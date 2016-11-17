/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package docs.home.scaladsl.serialization

import com.lightbend.lagom.scaladsl.playjson.{Migration, Migrations, SerializerRegistry, Serializers}
import play.api.libs.json.Format

import scala.collection.immutable.{Seq, SortedMap}

object BlogCommands {
  val serializers = Seq(
    AddComment.format,
    AddPost.format
  )
}

object BlogEvents {
  val serializers = Seq.empty[Format[_]]
}

//#registry
class MyRegistry extends SerializerRegistry {

  override val serializers = BlogCommands.serializers ++ BlogEvents.serializers
}
//#registry


