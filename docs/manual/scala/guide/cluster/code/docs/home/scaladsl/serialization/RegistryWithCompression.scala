/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package docs.home.scaladsl.serialization

//#registry-compressed
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}

object RegistryWithCompression extends JsonSerializerRegistry {
  override val serializers = Vector(

    // 'AddComment' uses `apply[T]()` .
    JsonSerializer[AddComment],

    // The AddPost message is usually rather big, so we want it compressed
    // when it's too large.
    JsonSerializer.compressed[AddPost]

  )
}
//#registry-compressed
