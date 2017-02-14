/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.maven

import javax.inject.{ Inject, Singleton }

import org.codehaus.plexus.logging.Logger
import play.dev.filewatch.LoggerProxy

/**
 * Logger
 */
@Singleton
class MavenLoggerProxy @Inject() (logger: Logger) extends LoggerProxy {
  override def debug(message: => String): Unit = {
    if (logger.isDebugEnabled) {
      logger.debug(message)
    }
  }

  override def info(message: => String): Unit = {
    if (logger.isInfoEnabled) {
      logger.info(message)
    }
  }

  override def warn(message: => String): Unit = {
    if (logger.isWarnEnabled) {
      logger.warn(message)
    }
  }

  override def error(message: => String): Unit = {
    if (logger.isErrorEnabled) {
      logger.error(message)
    }
  }

  override def verbose(message: => String): Unit = debug(message)

  override def success(message: => String): Unit = info(message)

  override def trace(t: => Throwable): Unit = {
    if (logger.isDebugEnabled) {
      logger.debug(t.getMessage, t)
    }
  }
}
