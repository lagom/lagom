/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.helloworld.impl

import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import com.example.helloworld.api.HelloWorldService
import com.example.helloworld.impl.readsides.StartedReadSideProcessor
import com.example.helloworld.impl.readsides.StoppedReadSideProcessor
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.softwaremill.macwire._

class HelloWorldLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new HelloWorldApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new HelloWorldApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[HelloWorldService])
}

abstract class HelloWorldApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
    with CassandraPersistenceComponents
    with AhcWSComponents {

  override lazy val lagomServer: LagomServer =
    serverFor[HelloWorldService](wire[HelloWorldServiceImpl])
  override lazy val jsonSerializerRegistry: JsonSerializerRegistry =
    HelloWorldSerializerRegistry
  persistentEntityRegistry.register(wire[HelloWorldEntity])

  private lazy val startedProcessor = wire[StartedReadSideProcessor]
  private lazy val stoppedProcessor = wire[StoppedReadSideProcessor]

  // send a request before registering the processor (this is an edge case, not a requisite)
  projections.startAllWorkers(StartedReadSideProcessor.Name)

  readSide.register(startedProcessor)
  readSide.register(stoppedProcessor)

}
