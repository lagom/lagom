/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package docs.home.scaladsl.serialization.v2a

import com.lightbend.lagom.scaladsl.playjson.{Migration, Migrations, SerializerRegistry, Serializers}

//#rename-class
case class OrderPlaced(shoppingCartId: String)
//#rename-class


class ShopSerializerRegistry extends SerializerRegistry {

  override def serializers = Vector.empty[Serializers[_]]

  //#rename-class-migration
  override def migrations: Map[String, Migration] = Map(
    Migrations.renamed(
      fromClassName = "com.lightbend.lagom.shop.OrderAdded",
      inVersion = 2,
      toClass = classOf[OrderPlaced])
  )
  //#rename-class-migration
}