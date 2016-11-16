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


object MigrationSample1 {
  import v2b.ItemAdded
  object ShopCommands {
    val serializers = Seq.empty[Format[_]]
  }

  object ShopEvents {
    val serializers = Seq.empty[Format[_]]
  }


  //#imperative
  class MySerializerRegistry extends SerializerRegistry {

    override val serializers = ShopCommands.serializers ++ ShopEvents.serializers

    import play.api.libs.json._

    private val itemAddedMigration = new Migration(2) {
      override def transform(fromVersion: Int, json: JsObject): JsObject = {
        if (fromVersion < 2) {
          json + ("discount" -> JsNumber(0.0D))
        } else {
          json
        }
      }
    }

    override def migrations = Map[String, Migration](
      classOf[ItemAdded].getName -> itemAddedMigration
    )
  }
  //#imperative

  //#transformers
  class ShopSerializerRegistry extends SerializerRegistry {

    override val serializers = ShopCommands.serializers ++ ShopEvents.serializers

    import play.api.libs.json._
    override def migrations = Map[String, Migration](
      Migrations.transform[ItemAdded](SortedMap(
        1 ->  JsPath.json.update((JsPath \ "discount").json.put(JsNumber(0.0D)))
      ))
    )
  }
  //#transformers

}