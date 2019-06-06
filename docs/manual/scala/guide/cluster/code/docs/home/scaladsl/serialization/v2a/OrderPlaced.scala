/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.serialization.v2a

import com.lightbend.lagom.scaladsl.playjson.JsonMigration
import com.lightbend.lagom.scaladsl.playjson.JsonMigrations
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer

//#rename-class
case class OrderPlaced(shoppingCartId: String)
//#rename-class

class ShopSerializerRegistry extends JsonSerializerRegistry {

  override def serializers = Vector.empty[JsonSerializer[_]]

  //#rename-class-migration
  override def migrations: Map[String, JsonMigration] = Map(
    JsonMigrations
      .renamed(fromClassName = "com.lightbend.lagom.shop.OrderAdded", inVersion = 2, toClass = classOf[OrderPlaced])
  )
  //#rename-class-migration
}
