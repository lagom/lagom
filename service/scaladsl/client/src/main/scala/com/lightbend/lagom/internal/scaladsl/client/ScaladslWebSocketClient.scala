/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.client

import com.lightbend.lagom.internal.client.WebSocketClient
import com.typesafe.config.Config
import io.netty.channel.EventLoopGroup
import play.api.Environment
import play.api.inject.ApplicationLifecycle

import scala.concurrent.ExecutionContext

class ScaladslWebSocketClient(environment: Environment, config: Config, eventLoop: EventLoopGroup, lifecycle: ApplicationLifecycle)(implicit ec: ExecutionContext) extends WebSocketClient(environment, config, eventLoop, lifecycle) with ScaladslServiceApiBridge {

  // Constructor that manages its own event loop
  def this(environment: Environment, config: Config, applicationLifecycle: ApplicationLifecycle)(implicit ec: ExecutionContext) = {
    this(environment, config, WebSocketClient.createEventLoopGroup(applicationLifecycle), applicationLifecycle)
  }
}
