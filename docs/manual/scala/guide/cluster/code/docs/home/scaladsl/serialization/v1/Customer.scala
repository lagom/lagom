/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package docs.home.scaladsl.serialization.v1

//#structural
case class Customer(
    name: String,
    street: String,
    city: String,
    zipCode: String,
    country: String)
//#structural