/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package docs.home.scaladsl.serialization.v1

import com.lightbend.lagom.scaladsl.playjson.Jsonable

//#add-optional
case class ItemAdded(
    shoppingCartId: String,
    productId: String,
    quantity: Int) extends Jsonable
//#add-optional