# Configuring Logging

Lagom uses SLF4J for logging, backed by [Logback](https://logback.qos.ch/) as its default logging engine.  See the [Logback documentation](https://logback.qos.ch/manual/configuration.html) for details on configuration.

## Default configuration

If you don't provide your own Logback configuration, Lagom uses the following default configuration in development:

@[](code/logback-lagom-dev.xml)

While it uses the following one in production:

@[](code/logback-lagom-default.xml)

A few things to note:

* The logger logs full exception stack traces and full-qualified logger names.
* Lagom uses ANSI color codes by default in level messages.
* In production, Lagom puts the logger behind the logback [AsyncAppender](https://logback.qos.ch/manual/appenders.html#AsyncAppender).  For details on the performance implications on this, see this [blog post](https://blog.takipi.com/how-to-instantly-improve-your-java-logging-with-7-logback-tweaks/).

## Custom configuration

For any custom configuration, you need to provide your own Logback configuration file.

### Using a configuration file from project source

You can provide a default logging configuration by creating a `logback.xml` file in the project's resource folder. Furthermore, for testing purposes, you can also create a `logback-test.xml` and place it in the `src/test/resources` directory of your project. When both `logback.xml` and `logback-test.xml` are in the classpath, the latter has higher precedence.

### Using an external configuration file

You can also specify a configuration file via a System property.  This is particularly useful for production environments where the configuration file may be managed outside of your application source.

> Note: The logging system gives top preference to configuration files specified by system properties, secondly to files in the resource directory, and lastly to the default. This allows you to customize your application's logging configuration and still override it for specific environments or developer setups.

#### Using `-Dlogger.resource`

To specify a configuration file to be loaded from the classpath use the system property `-Dlogger.resource`, e.g., `-Dlogger.resource=prod-logger.xml`.


#### Using `-Dlogger.file`

To specify a configuration file to be loaded from the file system use the system property `-Dlogger.file`, e.g., `-Dlogger.file=/opt/prod/logger.xml`.

## Play Lagom applications

When integrating a Play application in Lagom via the `LagomPlay` sbt plugin, the default Play logging module is used. The main difference, with respect to the Lagom logging module, is that the Play logging module provides different default logback configurations. Read the Play framework [Configuring Logging](https://www.playframework.com/documentation/2.6.x/SettingsLogger) documentation for details.

## Internal framework logging

It can be useful at times to gain more visibility on what's happening inside Lagom.

### Lagom logging configuration

Lagom system logging can be done by changing the `com.lightbend.lagom` logger to DEBUG.

```xml
<!-- Set logging for all Lagom library classes to DEBUG -->
<logger name="com.lightbend.lagom" level="DEBUG" />
```

### Akka logging configuration

Akka system logging can be done by changing the `akka` logger to DEBUG.

```xml
<!-- Set logging for all Akka library classes to DEBUG -->
<logger name="akka" level="DEBUG" />
<!-- Set a specific actor to DEBUG -->
<logger name="actors.MyActor" level="DEBUG" />
```

And, you will also need to add the following in your project's `application.conf`:

```conf
akka.loglevel=DEBUG
```

Furthermore, you may also wish to configure an appender for the Akka loggers that includes useful properties such as thread and actor address.  For more information about configuring Akka's logging, including details on Logback and Slf4j integration, see the [Akka documentation](https://doc.akka.io/docs/akka/2.6/logging.html).

### Play logging configuration

Play system logging can be done by changing the `play` logger to DEBUG.

```xml
<!-- Set logging for all Play library classes to DEBUG -->
<logger name="play" level="DEBUG" />
```

## Default Log4j2 configuration

Similarly to the default logback configuration, when using the Log4j2 Lagom module, a default configuration is provided. In development, the following is used by default:

@[](code/log4j2-lagom-dev.xml)

And in production, the following is the default configuration:

@[](code/log4j2-lagom-default.xml)

A few things to note:

* A file appender that writes to `logs/application.log` is created.
* The file appender logs full stack traces while the console logger limits it to 10 lines.
* Console logging uses colored log levels by default.
* In production, all loggers are configured to use [async loggers](https://logging.apache.org/log4j/2.x/manual/async.html) by default using the LMAX Disruptor library.

### Custom configuration

Including a file named `log4j2.xml` in your project's root will override the defaults. All other system properties specified for the logback integration above are also supported here.

## Using a Custom Logging Framework

Lagom uses Logback by default, but it is possible to configure Lagom to use another logging framework, as long as there is an SLF4J adapter for it.

If you're using Maven, you simply need to remove the logback dependency from your projects dependencies.  If using sbt, you need to disable the `LagomLogback` plugin in your sbt project:

@[lagom-logback-plugin-disabled](code/build-log-lang.sbt)

From there, a custom logging framework can be used.  Here, Log4J 2 is used as an example.

@[lagom-logback-plugin-disabled-log4j](code/build-log-lang.sbt)

Once the libraries and the SLF4J adapter are loaded, the `log4j.configurationFile` system property can be set on the command line as usual.
