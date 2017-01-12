/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package docs.home.scaladsl.serialization

import com.lightbend.lagom.scaladsl.playjson.{Jsonable, Serializers}
import play.api.libs.json.{Format, JsObject, Json, Reads}


object AddOrder {

  //#manualMapping
  case class AddOrder(productId: String, quantity: Int) extends Jsonable

  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  val format: Format[AddOrder] = (
    (JsPath \ "product_id").format[String] and
    (JsPath \ "quantity").format[Int]
  )(AddOrder.apply, unlift(AddOrder.unapply))
  //#manualMapping

}


object OrderCommands {

  //#singleton
  object GetOrders extends Jsonable

  val format = Serializers.emptySingletonFormat(GetOrders)
  //#singleton

}

object Hierarchy {


  //#hierarchy
  sealed trait Fruit
  case object Pear extends Fruit
  case object Apple extends Fruit
  case class Banana(ripe: Boolean) extends Fruit

  import play.api.libs.json._
  implicit val bananaFormat = Json.format[Banana]

  val format = Format[Fruit](
    Reads { js =>
      // use the fruitType field to determine how to deserialize
      val fruitType = (JsPath \ "fruitType").read[String].reads(js)
      fruitType.fold(
        errors => JsError("fruitType undefined or incorrect"),
        {
          case "pear" => JsSuccess(Pear)
          case "apple" => JsSuccess(Apple)
          case "banana" => (JsPath \ "data").read[Banana].reads(js)
        }
      )
    },
    Writes {
      case Pear => JsObject(Seq("fruitType" -> JsString("pear")))
      case Apple => JsObject(Seq("fruitType" -> JsString("apple")))
      case b: Banana => JsObject(Seq(
        "fruitType" -> JsString("banana"),
        "data" -> bananaFormat.writes(b)
      ))
    }
  )
  //#hierarchy

}
