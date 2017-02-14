/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.sbt

import play.dev.filewatch.LoggerProxy
import sbt.Logger

class SbtLoggerProxy(logger: Logger) extends LoggerProxy {
  override def debug(message: => String): Unit = logger.debug(message)

  override def info(message: => String): Unit = logger.info(message)

  override def warn(message: => String): Unit = logger.warn(message)

  override def error(message: => String): Unit = logger.error(message)

  override def verbose(message: => String): Unit = logger.verbose(message)

  override def success(message: => String): Unit = logger.success(message)

  override def trace(t: => Throwable): Unit = logger.trace(t)
}
