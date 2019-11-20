/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.api

import java.time.Instant

import akka.{Done, NotUsed}
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}
import play.api.libs.json.{Format, Json}


trait ShoppingCartService extends Service {

  def get(id: String): ServiceCall[NotUsed, String]

  def getReport(id: String): ServiceCall[NotUsed, String]

  def updateItem(id: String, productId: String, qty: Int): ServiceCall[NotUsed, String]

  def checkout(id: String): ServiceCall[NotUsed, String]

  override final def descriptor = {
    import Service._
    named("shopping-cart")
      .withCalls(
        restCall(Method.GET, "/shoppingcart/:id", get _),
        restCall(Method.GET, "/shoppingcart/:id/report", getReport _),
        // for the RESTafarians, my formal apologies but the GET calls below do mutate state
        // we just want an easy way to mutate data from a sbt scripted test, so no POST/PUT here
        restCall(Method.GET, "/shoppingcart/:id/:productId/:num", updateItem _),
        restCall(Method.GET, "/shoppingcart/:id/checkout", checkout _)
      )
      .withAutoAcl(true)
  }
}
