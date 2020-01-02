/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.serialization

//#registry-compressed
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry

object RegistryWithCompression extends JsonSerializerRegistry {
  override val serializers = Vector(
    // 'ItemAdded' uses `apply[T]()` .
    JsonSerializer[ItemAdded],
    // The OrderAdded message is usually rather big, so we want it compressed
    // when it's too large.
    JsonSerializer.compressed[OrderAdded]
  )
}
//#registry-compressed
