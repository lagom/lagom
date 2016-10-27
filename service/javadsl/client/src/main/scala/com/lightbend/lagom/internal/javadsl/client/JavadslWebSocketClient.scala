/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.client

import javax.inject.{ Inject, Singleton }

import com.lightbend.lagom.internal.client.WebSocketClient
import io.netty.channel.EventLoopGroup
import play.api.Environment
import play.api.inject.ApplicationLifecycle

import scala.concurrent.ExecutionContext

@Singleton
class JavadslWebSocketClient(environment: Environment, eventLoop: EventLoopGroup,
                             lifecycle: ApplicationLifecycle)(implicit ec: ExecutionContext) extends WebSocketClient(environment, eventLoop, lifecycle) with JavadslServiceApiBridge {

  // Constructor that manages its own event loop
  @Inject
  def this(environment: Environment, applicationLifecycle: ApplicationLifecycle)(implicit ec: ExecutionContext) = {
    this(environment, WebSocketClient.createEventLoopGroup(applicationLifecycle), applicationLifecycle)
  }
}
