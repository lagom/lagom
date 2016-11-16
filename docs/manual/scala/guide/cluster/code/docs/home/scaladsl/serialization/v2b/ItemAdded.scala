/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package docs.home.scaladsl.serialization.v2b

import com.lightbend.lagom.scaladsl.playjson.Jsonable

//#add-mandatory
case class ItemAdded(
    shoppingCartId: String,
    productId: String,
    quantity: Int,
    discount: Double) extends Jsonable
//#add-mandatory