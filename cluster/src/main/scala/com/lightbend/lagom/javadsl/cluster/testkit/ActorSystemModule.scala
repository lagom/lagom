/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.cluster.testkit

import akka.actor.ActorSystem
import com.google.inject.AbstractModule
import com.google.inject.Provides

import scala.concurrent.ExecutionContext

class ActorSystemModule(system: ActorSystem) extends AbstractModule {

  override def configure(): Unit = ()

  @Provides
  def provideActorSystem: ActorSystem = system

  @Provides
  def provideExecutionContext: ExecutionContext = system.dispatcher

}
