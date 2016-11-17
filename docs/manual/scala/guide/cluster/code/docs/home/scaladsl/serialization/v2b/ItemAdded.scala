/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package docs.home.scaladsl.serialization.v2b

import com.lightbend.lagom.scaladsl.playjson.{Jsonable, Migration, Migrations, SerializerRegistry}
import docs.home.scaladsl.serialization.v2b
import play.api.libs.json.Format

import scala.collection.immutable

//#add-mandatory
case class ItemAdded(
    shoppingCartId: String,
    productId: String,
    quantity: Int,
    discount: Double) extends Jsonable
//#add-mandatory


object ItemAddedMigration {
  import v2b.ItemAdded
  object ShopCommands {
    val serializers = Seq.empty[Format[_]]
  }

  object ShopEvents {
    val serializers = Seq.empty[Format[_]]
  }


  //#imperative-migration
  class ShopSerializerRegistry extends SerializerRegistry {

    import play.api.libs.json._

    override val serializers = ShopCommands.serializers ++ ShopEvents.serializers

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
  //#imperative-migration

}

object ItemAddedMigrationTransformer {
  import v2b.ItemAdded
  object ShopCommands {
    val serializers = immutable.Seq.empty[Format[_]]
  }

  object ShopEvents {
    val serializers = immutable.Seq.empty[Format[_]]
  }

  //#transformer-migration
  class ShopSerializerRegistry extends SerializerRegistry {

    import play.api.libs.json._

    override val serializers = ShopCommands.serializers ++ ShopEvents.serializers

    val addDefaultDiscount = JsPath.json.update((JsPath \ "discount").json.put(JsNumber(0.0D)))

    override def migrations = Map[String, Migration](
      Migrations.transform[ItemAdded](immutable.SortedMap(
        1 -> addDefaultDiscount
      ))
    )
  }
  //#transformer-migration

}
