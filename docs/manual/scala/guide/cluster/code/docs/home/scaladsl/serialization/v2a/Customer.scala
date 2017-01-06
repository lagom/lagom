/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package docs.home.scaladsl.serialization.v2a

import com.lightbend.lagom.scaladsl.playjson.{Migration, SerializerRegistry}
import play.api.libs.json.{JsObject, JsPath, Json, Reads}

import scala.collection.immutable.Seq


//#structural
case class Address(
    street: String,
    city: String,
    zipCode: String,
    country: String)

case class Customer(
    name: String,
    address: Address,
    shippingAddress: Option[Address])
//#structural


object Customer {
  implicit val addressFormat = Json.format[Address]
  val customerFormat = Json.format[Customer]
}

class CustomerMigration extends SerializerRegistry {

  override def serializers = Seq.empty


  //#structural-migration
  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  val customerMigration = new Migration(2) {

    // use arbitrary logic to parse an Address
    // out of the old schema
    val readOldAddress: Reads[Address] = (
      (JsPath \ "street").read[String] and
      (JsPath \ "city").read[String] and
      (JsPath \ "zipCode").read[String] and
      (JsPath \ "country").read[String]
    )(Address)


    override def transform(fromVersion: Int, json: JsObject): JsObject = {
      if (fromVersion < 2) {
        val address = readOldAddress.reads(json).get
        val withoutOldAddress = json - "street" - "city" - "zipCode" - "country"

        // use existing formatter to write the address in the new schema
        withoutOldAddress + ("address" -> Customer.addressFormat.writes(address))
      } else {
        json
      }
    }
  }

  override def migrations: Map[String, Migration] = Map(
    classOf[Customer].getName -> customerMigration
  )
  //#structural-migration
}
