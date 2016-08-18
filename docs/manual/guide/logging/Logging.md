# Logging

Lagom uses SLF4J for logging, backed by [Logback](http://logback.qos.ch/) as its default logging engine. Here is a short example showcasing how you can access the logger:

@[](code/docs/logging/LoggingExample.java)

And you can read of more advanced usages on the [SLF4J user manual](http://www.slf4j.org/manual.html).

If you're using maven, you'll need to add the Lagom logback dependency to your classpath to get Logback support:

```xml
<dependency>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-logback_2.11</artifactId>
    <version>${lagom.version}</version>
</dependency>
```

If you're using sbt, you'll automatically get the Lagom logback support when you enable the `LagomJava` plugin on a project:

@[lagom-logback-plugin-lagomjava](code/build-log.sbt)

If you'd like to use the Lagom logger module on a project that doesn't have the `LagomJava` sbt plugin enabled (e.g., a Lagom API project), simply add the Lagom logger module as an explicit library dependency:

@[lagom-logback-libdep](code/build-log.sbt)

The Lagom logger module includes a default logback configuration. Read [[Configuring Logging|SettingsLogger]] for details.
