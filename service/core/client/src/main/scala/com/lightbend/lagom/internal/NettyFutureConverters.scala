/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal

import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.util.concurrent.GenericFutureListener
import io.netty.util.concurrent.{ Future => NettyFuture }

import scala.concurrent.Future
import scala.concurrent.Promise

object NettyFutureConverters {

  implicit class ToFuture[T](future: NettyFuture[T]) {
    def toScala: Future[T] = {
      val promise = Promise[T]()
      future.addListener(new GenericFutureListener[NettyFuture[T]] {
        def operationComplete(future: NettyFuture[T]) = {
          if (future.isSuccess) {
            promise.success(future.getNow)
          } else if (future.isCancelled) {
            promise.failure(new RuntimeException("Future cancelled"))
          } else {
            promise.failure(future.cause())
          }
        }
      })
      promise.future
    }
  }

  implicit class ChannelFutureToFuture(future: ChannelFuture) {
    def channelFutureToScala: Future[Channel] = {
      val promise = Promise[Channel]()
      future.addListener(new ChannelFutureListener {
        def operationComplete(future: ChannelFuture) = {
          if (future.isSuccess) {
            promise.success(future.channel())
          } else if (future.isCancelled) {
            promise.failure(new RuntimeException("Future cancelled"))
          } else {
            promise.failure(future.cause())
          }
        }
      })
      promise.future
    }
  }

}
