/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.client

import com.lightbend.lagom.internal.client.WebSocketClient
import io.netty.channel.EventLoopGroup
import play.api.Environment
import play.api.inject.ApplicationLifecycle

import scala.concurrent.ExecutionContext

class ScaladslWebSocketClient(environment: Environment, eventLoop: EventLoopGroup, lifecycle: ApplicationLifecycle)(implicit ec: ExecutionContext) extends WebSocketClient(environment, eventLoop, lifecycle) with ScaladslServiceApiBridge {

  // Constructor that manages its own event loop
  def this(environment: Environment, applicationLifecycle: ApplicationLifecycle)(implicit ec: ExecutionContext) = {
    this(environment, WebSocketClient.createEventLoopGroup(applicationLifecycle), applicationLifecycle)
  }
}
