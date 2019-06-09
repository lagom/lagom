/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LoggingExample {
  private final val log: Logger =
    LoggerFactory.getLogger(classOf[LoggingExample])

  def demonstrateLogging(msg: String): Unit = {
    log.debug("Here is a message at debug level: {}.", msg)
  }
}
