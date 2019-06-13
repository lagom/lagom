/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.scaladsl.client

import com.lightbend.lagom.internal.client.WebSocketClient
import com.lightbend.lagom.internal.client.WebSocketClientConfig
import io.netty.channel.EventLoopGroup
import play.api.Environment
import play.api.inject.ApplicationLifecycle

import scala.concurrent.ExecutionContext

private[lagom] class ScaladslWebSocketClient(
    environment: Environment,
    config: WebSocketClientConfig,
    eventLoop: EventLoopGroup,
    lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext)
    extends WebSocketClient(environment, config, eventLoop, lifecycle)
    with ScaladslServiceApiBridge {

  // Constructor that manages its own event loop
  def this(environment: Environment, config: WebSocketClientConfig, applicationLifecycle: ApplicationLifecycle)(
      implicit ec: ExecutionContext
  ) = {
    this(environment, config, WebSocketClient.createEventLoopGroup(applicationLifecycle), applicationLifecycle)
  }
}
