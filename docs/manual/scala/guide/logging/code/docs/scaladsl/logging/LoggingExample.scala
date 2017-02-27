package docs.scaladsl.logging

import org.slf4j.{ Logger, LoggerFactory }

class LoggingExample {
  private final val log: Logger =
    LoggerFactory.getLogger(classOf[LoggingExample])

  def demonstrateLogging(msg: String): Unit = {
    log.debug("Here is a message at debug level: {}.", msg)
  }
}
