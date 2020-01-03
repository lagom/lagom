/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.serialization

import play.api.libs.json.Format
import play.api.libs.json.Json

case class ItemAdded(shoppingCartId: String, productId: String, quantity: Int, discount: Double)

object ItemAdded {
  //#format
  implicit val format: Format[ItemAdded] = Json.format
  //#format
}
