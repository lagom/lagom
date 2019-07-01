/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package impl

import api.BarService
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.cluster.ClusterComponents
import com.lightbend.lagom.scaladsl.playjson.EmptyJsonSerializerRegistry
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire._
import play.api.libs.ws.ahc.AhcWSComponents

class BarLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new BarApplication(context) {
      override def serviceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new BarApplication(context) with LagomDevModeComponents
}

abstract class BarApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with AhcWSComponents
    with ClusterComponents {

  override lazy val lagomServer = serverFor[BarService](wire[BarServiceImpl])

  lazy val jsonSerializerRegistry = EmptyJsonSerializerRegistry
}
