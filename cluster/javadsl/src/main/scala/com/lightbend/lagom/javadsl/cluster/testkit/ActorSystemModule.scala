/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.cluster.testkit

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import com.google.inject.AbstractModule
import com.google.inject.Provides

import scala.concurrent.ExecutionContext

class ActorSystemModule(system: ActorSystem) extends AbstractModule {

  private lazy val mat = ActorMaterializer()(system)
  override def configure(): Unit = ()

  @Provides
  def provideActorSystem: ActorSystem = system

  @Provides
  def provideMaterializer: Materializer = mat

  @Provides
  def provideExecutionContext: ExecutionContext = system.dispatcher

}
