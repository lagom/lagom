/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.client

import javax.inject.Inject
import javax.inject.Singleton

import com.lightbend.lagom.internal.client.WebSocketClient
import com.lightbend.lagom.internal.client.WebSocketClientConfig
import com.typesafe.config.Config
import io.netty.channel.EventLoopGroup
import play.api.Environment
import play.api.inject.ApplicationLifecycle

import scala.concurrent.ExecutionContext

@Singleton
class JavadslWebSocketClient(
    environment: Environment,
    config: WebSocketClientConfig,
    eventLoop: EventLoopGroup,
    lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext)
    extends WebSocketClient(environment, config, eventLoop, lifecycle)
    with JavadslServiceApiBridge {

  // Constructor that manages its own event loop
  @Inject
  def this(environment: Environment, config: Config, applicationLifecycle: ApplicationLifecycle)(
      implicit ec: ExecutionContext
  ) = {
    this(
      environment,
      WebSocketClientConfig(config),
      WebSocketClient.createEventLoopGroup(applicationLifecycle),
      applicationLifecycle
    )
  }
}
