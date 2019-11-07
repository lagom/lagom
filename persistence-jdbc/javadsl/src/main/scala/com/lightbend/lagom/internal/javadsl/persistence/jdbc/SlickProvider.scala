/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.persistence.jdbc

import javax.inject.Inject
import javax.inject.Singleton
import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown

import scala.concurrent.ExecutionContext

@Singleton
class SlickProvider @Inject() (system: ActorSystem, coordinatedShutdown: CoordinatedShutdown)(
    implicit ec: ExecutionContext
) extends com.lightbend.lagom.internal.persistence.jdbc.SlickProvider(system, coordinatedShutdown)(ec)
