/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package docs.home.scaladsl.serialization.v1

//#add-optional
case class ItemAdded(
    shoppingCartId: String,
    productId: String,
    quantity: Int)
//#add-optional
