/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal

import io.netty.channel.{ Channel, ChannelFuture, ChannelFutureListener }
import io.netty.util.concurrent.{ Future => NettyFuture, GenericFutureListener }

import scala.concurrent.{ Future, Promise }

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
