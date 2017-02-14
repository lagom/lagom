/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package docs.home.scaladsl.serialization.v2a

import com.lightbend.lagom.scaladsl.playjson.{JsonMigration, JsonMigrations, JsonSerializerRegistry, JsonSerializer}

//#rename-class
case class OrderPlaced(shoppingCartId: String)
//#rename-class


class ShopSerializerRegistry extends JsonSerializerRegistry {

  override def serializers = Vector.empty[JsonSerializer[_]]

  //#rename-class-migration
  override def migrations: Map[String, JsonMigration] = Map(
    JsonMigrations.renamed(
      fromClassName = "com.lightbend.lagom.shop.OrderAdded",
      inVersion = 2,
      toClass = classOf[OrderPlaced])
  )
  //#rename-class-migration
}
