/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.serialization

import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.libs.json.Reads

object AddOrder {

  //#manualMapping
  case class AddOrder(productId: String, quantity: Int)

  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  object AddOrder {
    implicit val format: Format[AddOrder] =
      (JsPath \ "product_id")
        .format[String]
        .and((JsPath \ "quantity").format[Int])
        .apply(AddOrder.apply, unlift(AddOrder.unapply))
  }
  //#manualMapping

}

object OrderCommands {

  //#singleton
  case object GetOrders {
    implicit val format: Format[GetOrders.type] =
      JsonSerializer.emptySingletonFormat(GetOrders)
  }
  //#singleton

}

object Hierarchy {

  //#hierarchy
  import play.api.libs.json._

  sealed trait Fruit
  case object Pear                 extends Fruit
  case object Apple                extends Fruit
  case class Banana(ripe: Boolean) extends Fruit

  object Banana {
    implicit val format: Format[Banana] = Json.format
  }

  object Fruit {
    implicit val format = Format[Fruit](
      Reads { js =>
        // use the fruitType field to determine how to deserialize
        val fruitType = (JsPath \ "fruitType").read[String].reads(js)
        fruitType.fold(
          errors => JsError("fruitType undefined or incorrect"), {
            case "pear"   => JsSuccess(Pear)
            case "apple"  => JsSuccess(Apple)
            case "banana" => (JsPath \ "data").read[Banana].reads(js)
          }
        )
      },
      Writes {
        case Pear  => JsObject(Seq("fruitType" -> JsString("pear")))
        case Apple => JsObject(Seq("fruitType" -> JsString("apple")))
        case b: Banana =>
          JsObject(
            Seq(
              "fruitType" -> JsString("banana"),
              "data"      -> Banana.format.writes(b)
            )
          )
      }
    )
  }
  //#hierarchy

}
