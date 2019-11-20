/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.serialization

import play.api.libs.json.Format
import play.api.libs.json.Json

case class OrderAdded(shoppingCartId: String)

object OrderAdded {
  implicit val format: Format[OrderAdded] = Json.format
}

object ManualMappingOrderAdded {
  //#manualMapping
  case class OrderAdded(productId: String, quantity: Int)

  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  object OrderAdded {
    implicit val format: Format[OrderAdded] =
      (JsPath \ "product_id")
        .format[String]
        .and((JsPath \ "quantity").format[Int])
        .apply(AddOrder.apply, unlift(AddOrder.unapply))
  }
  //#manualMapping
}
