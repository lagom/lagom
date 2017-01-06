/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package docs.home.scaladsl.serialization

import com.lightbend.lagom.scaladsl.playjson.{SerializerRegistry, Serializers}

import scala.collection.immutable

object BlogCommands {
  val serializers =  Vector[Serializers[_]](
    Serializers(AddComment.format),
    Serializers(AddPost.format)
  )
}

object BlogEvents {
  val serializers = Vector.empty[Serializers[_]]
}

//#registry
class MyRegistry extends SerializerRegistry {

  override val serializers = BlogCommands.serializers ++ BlogEvents.serializers
}
//#registry


