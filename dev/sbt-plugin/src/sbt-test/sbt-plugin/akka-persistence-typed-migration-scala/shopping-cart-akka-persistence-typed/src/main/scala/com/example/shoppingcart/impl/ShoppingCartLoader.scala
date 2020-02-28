/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.impl

import com.example.shoppingcart.api.ShoppingCartService
import com.lightbend.lagom.scaladsl.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.slick.SlickPersistenceComponents
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire._
import play.api.db.HikariCPComponents
import play.api.libs.ws.ahc.AhcWSComponents
import akka.cluster.sharding.typed.scaladsl.Entity
import com.lightbend.lagom.scaladsl.playjson.EmptyJsonSerializerRegistry

class ShoppingCartLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new ShoppingCartApplication(context) with LagomDevModeComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new ShoppingCartApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[ShoppingCartService])
}

abstract class ShoppingCartApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
    with SlickPersistenceComponents
    with HikariCPComponents
    with AhcWSComponents {

  // Bind the service that this server provides
  override lazy val lagomServer = serverFor[ShoppingCartService](wire[ShoppingCartServiceImpl])

  // Register the JSON serializer registry
  override lazy val jsonSerializerRegistry = ShoppingCartSerializerRegistry

  lazy val reportRepository = wire[ShoppingCartReportRepository]
  readSide.register(wire[ShoppingCartReportProcessor])

  //#akka-persistence-init-sharded-behavior
  // in Akka Typed, this is the equivalent of Lagom's PersistentEntityRegistry.register
  clusterSharding.init(
    Entity(ShoppingCart.typeKey) {
      ctx => ShoppingCart.behavior(ctx)
    }
  )
  //#akka-persistence-init-sharded-behavior

}
