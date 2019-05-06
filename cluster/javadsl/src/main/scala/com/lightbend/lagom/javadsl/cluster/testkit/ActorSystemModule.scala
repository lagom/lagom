/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.cluster.testkit

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import com.google.inject.AbstractModule
import com.google.inject.Provides

import scala.concurrent.ExecutionContext

class ActorSystemModule(system: ActorSystem) extends AbstractModule {

  private lazy val mat           = ActorMaterializer()(system)
  override def configure(): Unit = ()

  @Provides
  def provideActorSystem: ActorSystem = system

  @Provides
  def provideMaterializer: Materializer = mat

  @Provides
  def provideExecutionContext: ExecutionContext = system.dispatcher

}
