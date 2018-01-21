# Logging

Lagom uses SLF4J for logging, backed by [Logback](https://logback.qos.ch/) as its default logging engine. Here is a short example showcasing how you can access the logger:

@[](code/docs/scaladsl/logging/LoggingExample.scala)

And you can read of more advanced usages on the [SLF4J user manual](https://www.slf4j.org/manual.html).

If you're using maven, you'll need to add the Lagom logback dependency to your classpath to get Logback support:

```xml
<dependency>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-logback_${scala.binary.version}</artifactId>
    <version>${lagom.version}</version>
</dependency>
```

If you're using sbt, you'll automatically get the Lagom logback support when you enable the Lagom plugin on a project:

@[lagom-logback-plugin-lagomscala](code/build-log-lang.sbt)

If you'd like to use the Lagom logger module on a project that doesn't have the Lagom sbt plugin enabled (e.g., a Lagom API project), simply add the Lagom logger module as an explicit library dependency:

@[lagom-logback-libdep](code/build-log.sbt)

The Lagom logger module includes a default logback configuration. Read [[Configuring Logging|SettingsLogger]] for details.

## Log4j 2

Lagom can be configured to use [Log4j 2](https://logging.apache.org/log4j/2.x/) as its default logging engine. Using Log4j 2 can use either the SLF4J API for logging as shown above or the Log4j 2 API. For example, using the Log4j 2 API:

@[](code/docs/scaladsl/logging/Log4j2Example.scala)

If you're using maven, you'll need to add the Lagom log4j2 dependency to your classpath to get Log4j 2 support:

```xml
<dependency>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-log4j2_${scala.binary.version}</artifactId>
    <version>${lagom.version}</version>
</dependency>
```

Note that this dependency replaces logback here, so remove any logback dependencies from your pom.xml as well.

If you're using sbt, you'll have to disable the `LagomLogback` plugin and enable the `LagomLog4j2` plugin instead. So for example, if you're using the Lagom plugin:

@[lagom-log4j2-plugin-lagomscala](code/build-log-lang.sbt)

When you're not using the Lagom plugin, add the Lagom Log4j2 module as a dependency:

@[lagom-log4j2-libdep](code/build-log.sbt)

The Lagom Log4j2 module also includes default logging configurations. Read [[Configuring Logging|SettingsLogger]] for details.
