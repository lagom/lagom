/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package impl

import api.FooService
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.cluster.ClusterComponents
import com.lightbend.lagom.scaladsl.playjson.EmptyJsonSerializerRegistry
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire._
import play.api.libs.ws.ahc.AhcWSComponents

class FooLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new FooApplication(context) {
      override def serviceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new FooApplication(context) with LagomDevModeComponents
}

abstract class FooApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with AhcWSComponents
    with ClusterComponents {

  override lazy val lagomServer = serverFor[FooService](wire[FooServiceImpl])

  lazy val jsonSerializerRegistry = EmptyJsonSerializerRegistry
}
