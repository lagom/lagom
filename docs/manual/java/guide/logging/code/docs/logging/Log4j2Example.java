package docs.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Log4j2Example {
    private static final Logger LOGGER = LogManager.getLogger();

    public void demonstrateLogging(String msg) {
        LOGGER.debug("Here is a message at the debug level: {}", msg);
    }
}