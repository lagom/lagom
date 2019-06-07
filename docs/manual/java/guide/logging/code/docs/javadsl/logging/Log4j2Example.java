/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Log4j2Example {
  private static final Logger LOGGER = LogManager.getLogger();

  public void demonstrateLogging(String msg) {
    LOGGER.debug("Here is a message at the debug level: {}", msg);
  }
}
