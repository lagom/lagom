# Logging

Lagom uses SLF4J for logging, backed by [Logback](http://logback.qos.ch/) as its default logging engine. Here is a short example showcasing how you can access the logger:

@[](code/docs/logging/LoggingExample.java)

And you can read of more advanced usages on the [SLF4J user manual](http://www.slf4j.org/manual.html).

The required library dependencies to SLF4J and Logback are automatically added to all projects that have the `LagomJava` sbt plugin enabled

@[lagom-logback-plugin-lagomjava](code/build-log.sbt)

If you'd like to use the Lagom logger module on a project that doesn't have the `LagomJava` sbt plugin enabled (e.g., a Lagom API project), simply add the Lagom logger module as an explicit library dependency:

@[lagom-logback-libdep](code/build-log.sbt)

The Lagom logger module includes a default logback configuration. Read [[Configuring Logging|SettingsLogger]] for details.
