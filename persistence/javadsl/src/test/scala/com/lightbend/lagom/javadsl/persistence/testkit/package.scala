/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import java.util.concurrent.CompletionStage
import java.util.function.BiConsumer

import akka.actor.ActorRef

package object testkit {

  implicit class pipe[T](val stage: CompletionStage[T]) extends AnyVal {
    def pipeTo(recipient: ActorRef): Unit = {
      stage.whenComplete(new BiConsumer[T, Throwable] {
        override def accept(value: T, e: Throwable): Unit = {
          if (value != null) recipient ! value
          if (e != null) recipient ! e
        }
      })
    }
  }
}
