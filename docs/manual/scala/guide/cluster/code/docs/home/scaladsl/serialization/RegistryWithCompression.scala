/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.serialization

//#registry-compressed
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry

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
