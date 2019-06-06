/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.logging

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object Log4j2Example {
  private final val Logger: Logger = LogManager.getLogger
}

class Log4j2Example {
  def demonstrateLogging(msg: String): Unit = {
    Log4j2Example.Logger.debug("Here is a message at the debug level: {}", msg)
  }
}
