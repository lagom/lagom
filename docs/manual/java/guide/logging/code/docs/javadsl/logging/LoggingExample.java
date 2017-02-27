package docs.javadsl.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingExample {
    private final Logger log = LoggerFactory.getLogger(LoggingExample.class);

    public void demonstrateLogging(String msg) {
        log.debug("Here is a message at debug level: {}.", msg);
    }
}
