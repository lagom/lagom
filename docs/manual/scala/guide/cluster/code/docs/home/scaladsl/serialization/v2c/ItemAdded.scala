/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.serialization.v2c

import com.lightbend.lagom.scaladsl.playjson.JsonMigration
import com.lightbend.lagom.scaladsl.playjson.JsonMigrations
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import play.api.libs.json.JsObject
import play.api.libs.json.JsPath
import play.api.libs.json.JsString

import scala.collection.immutable

//#rename
case class ItemAdded(shoppingCartId: String, itemId: String, quantity: Int)
//#rename

object ItemAddedMigration {

  class ShopSerializerRegistry1 extends JsonSerializerRegistry {
    override def serializers = Vector.empty

    //#imperative-migration
    private val itemAddedMigration = new JsonMigration(2) {
      override def transform(fromVersion: Int, json: JsObject): JsObject = {
        if (fromVersion < 2) {
          val productId = (JsPath \ "productId").read[JsString].reads(json).get
          json + ("itemId" -> productId) - "productId"
        } else {
          json
        }
      }
    }

    override def migrations = Map[String, JsonMigration](
      classOf[ItemAdded].getName -> itemAddedMigration
    )
    //#imperative-migration
  }

  class ShopSerializerRegistry2 extends JsonSerializerRegistry {

    override val serializers = Vector.empty

    //#transformer-migration
    val productIdToItemId =
      JsPath.json
        .update(
          (JsPath \ "itemId").json.copyFrom((JsPath \ "productId").json.pick)
        )
        .andThen((JsPath \ "productId").json.prune)

    override def migrations = Map[String, JsonMigration](
      JsonMigrations.transform[ItemAdded](
        immutable.SortedMap(
          1 -> productIdToItemId
        )
      )
    )
    //#transformer-migration
  }

}
