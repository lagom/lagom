package docs.home.scaladsl.persistence

import scala.collection.immutable

import com.lightbend.lagom.scaladsl.playjson.SerializerRegistry
import com.lightbend.lagom.scaladsl.playjson.Serializers

class BlogPostSerializerRegistry extends SerializerRegistry {

  override def serializers: immutable.Seq[Serializers[_]] =
    BlogCommand.serializers ++ BlogEvent.serializers ++ BlogState.serializers

}
