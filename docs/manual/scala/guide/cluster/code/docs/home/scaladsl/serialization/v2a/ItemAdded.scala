/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package docs.home.scaladsl.serialization.v2a

import com.lightbend.lagom.scaladsl.playjson.Jsonable

//#add-optional
case class ItemAdded(
    shoppingCartId: String,
    productId: String,
    quantity: Int,
    discount: Option[BigDecimal]) extends Jsonable
//#add-optional