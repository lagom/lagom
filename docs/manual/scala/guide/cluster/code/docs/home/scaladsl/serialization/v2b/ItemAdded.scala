/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.serialization.v2b

import com.lightbend.lagom.scaladsl.playjson._

import scala.collection.immutable

//#add-mandatory
case class ItemAdded(shoppingCartId: String, productId: String, quantity: Int, discount: Double)
//#add-mandatory

object ItemAddedMigration {

  object ShopCommands {
    val serializers = Vector.empty[JsonSerializer[_]]
  }

  object ShopEvents {
    val serializers = Vector.empty[JsonSerializer[_]]
  }

  //#imperative-migration
  class ShopSerializerRegistry extends JsonSerializerRegistry {

    import play.api.libs.json._

    override val serializers = ShopCommands.serializers ++ ShopEvents.serializers

    private val itemAddedMigration = new JsonMigration(2) {
      override def transform(fromVersion: Int, json: JsObject): JsObject = {
        if (fromVersion < 2) {
          json + ("discount" -> JsNumber(0.0d))
        } else {
          json
        }
      }
    }

    override def migrations = Map[String, JsonMigration](
      classOf[ItemAdded].getName -> itemAddedMigration
    )
  }
  //#imperative-migration

}

object ItemAddedMigrationTransformer {

  object ShopCommands {
    val serializers = immutable.Seq.empty[JsonSerializer[_]]
  }

  object ShopEvents {
    val serializers = immutable.Seq.empty[JsonSerializer[_]]
  }

  //#transformer-migration
  class ShopSerializerRegistry extends JsonSerializerRegistry {

    import play.api.libs.json._

    override val serializers = ShopCommands.serializers ++ ShopEvents.serializers

    val addDefaultDiscount = JsPath.json.update((JsPath \ "discount").json.put(JsNumber(0.0d)))

    override def migrations = Map[String, JsonMigration](
      JsonMigrations.transform[ItemAdded](
        immutable.SortedMap(
          1 -> addDefaultDiscount
        )
      )
    )
  }
  //#transformer-migration

}
