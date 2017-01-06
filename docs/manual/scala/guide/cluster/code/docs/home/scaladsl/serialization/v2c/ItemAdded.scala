/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package docs.home.scaladsl.serialization.v2c

import com.lightbend.lagom.scaladsl.playjson.{Jsonable, Migration, Migrations, SerializerRegistry}
import play.api.libs.json.{JsObject, JsPath, JsString}

import scala.collection.immutable

//#rename
case class ItemAdded(
    shoppingCartId: String,
    itemId: String,
    quantity: Int) extends Jsonable
//#rename


object ItemAddedMigration {

  class ShopSerializerRegistry1 extends SerializerRegistry {
    override def serializers = Vector.empty

    //#imperative-migration
    private val itemAddedMigration = new Migration(2) {
      override def transform(fromVersion: Int, json: JsObject): JsObject = {
        if (fromVersion < 2) {
          val productId = (JsPath \ "productId").read[JsString].reads(json).get
          json + ("itemId" -> productId) - "productId"
        } else {
          json
        }
      }
    }

    override def migrations = Map[String, Migration](
      classOf[ItemAdded].getName -> itemAddedMigration
    )
    //#imperative-migration
  }

  class ShopSerializerRegistry2 extends SerializerRegistry {

    override val serializers = Vector.empty

    //#transformer-migration
    val productIdToItemId =
    JsPath.json.update(
      (JsPath \ "itemId").json.copyFrom((JsPath \ "productId").json.pick)
    ) andThen (JsPath \ "productId").json.prune

    override def migrations = Map[String, Migration](
      Migrations.transform[ItemAdded](immutable.SortedMap(
        1 -> productIdToItemId
      ))
    )
    //#transformer-migration
  }

}
