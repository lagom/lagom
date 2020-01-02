/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.dev

/**
 * A subset replacement of play-file-watch's LoggerProxy, introduced to break the dependency on
 * play-file-watch which is Scala 2.12 only.
 */
private[lagom] trait MiniLogger {
  def debug(message: => String): Unit
  def info(message: => String): Unit
}
