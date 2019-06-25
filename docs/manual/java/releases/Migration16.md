
# Lagom 1.6 Migration Guide

This guide explains how to migrate from Lagom 1.5 to Lagom 1.6. If you are upgrading from an earlier version, be sure to review previous migration guides.

Lagom 1.6 updates to the latest major versions of Play (2.8), Akka (2.6) and Akka HTTP (10.1). We have highlighted the changes that are relevant to most Lagom users, but you may need to change code in your services that uses Play APIs directly. You'll also need to update any Play services in your Lagom project repositories to be compatible with Play 2.8. Please refer to the [Play 2.8 migration guide](https://www.playframework.com/documentation/2.8.0-M1/Migration28), [Akka Migration Guide 2.5.x to 2.6.x](https://doc.akka.io/docs/akka/2.6.0-M1/project/migration-guide-2.5.x-2.6.x.html) and the [Akka HTTP 10.1.x release announcements](https://akka.io/blog/news-archive.html) for more details.

For a detailed list of version upgrades of other libraries Lagom builds on such as for Slick, Kafka and others, refer to the [release notes](https://github.com/lagom/lagom/releases).

## Migrating from Lagom 1.5

To migrate from Lagom 1.5 we recommend first migrating to latest version of Lagom 1.5 before upgrading to Lagom 1.6. Refer to the [release notes](https://github.com/lagom/lagom/releases) for details upgrading to latest version of Lagom 1.5.

## Build changes

### Maven

If you're using a `lagom.version` property in the `properties` section of your root `pom.xml`, then simply update that to `1.6.0`. Otherwise, you'll need to go through every place that a Lagom dependency, including plugins, is used, and set the version there.

> **Note:** Lagom 1.6 requires, at least,  Maven `3.6.0`. Please update your environments.

### sbt

The version of Lagom can be updated by editing the `project/plugins.sbt` file, and updating the version of the Lagom sbt plugin. For example:

```scala
addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.6.0")
```

We also recommend upgrading to sbt 1.2.8 or later, by updating the `sbt.version` in `project/build.properties`.

### Jackson serialization

Lagom is now using the Jackson serializer from Akka, which is an improved version of the serializer in Lagom 1.5. You can find more information about the Akka Jackson serializer in the [Akka documentation](https://doc.akka.io/docs/akka/2.6/serialization-jackson.html). It is compatible with Lagom 1.5 in both directions.

#### JacksonJsonMigration

If you have used `JacksonJsonMigration` classes they must be changed to extend `akka.serialization.jackson.JacksonMigration` instead. It has the same method signatures as the deprecated `JacksonJsonMigration`.

The configuration in `lagom.serialization.json.migrations` must be moved to `akka.serialization.jackson.migrations`.
It has the same structure.

#### Service API

The default settings for the `ObjectMapper` that is used for JSON serialization in the Service API now uses `ISO-8601` date formats. The default in previous versions of Lagom was to use Jackson private format which can be more time and/or space efficient. The reason for this change is that the ISO format is a better default for interoperability.

If you want your Service API to produce the same output for types like `java.time.Instant`or `java.time.LocalDateTime` adjust the configuration for the `ObjectMapper` in your `application.conf`:

```
akka.serialization.jackson {
  # Configuration of the ObjectMapper for external service api
  jackson-json-serviceapi {
     WRITE_DATES_AS_TIMESTAMPS = on # Serializes dates using Jackson custom formats
  }
}

#### Configuration changes

* `lagom.serialization.json.compress-larger-than` is now configured with `akka.serialization.jackson.jackson-json-gzip.compress-larger-than`
* `lagom.serialization.json.jackson-modules` is now configured in `akka.serialization.jackson.jackson-modules`

#### JSON Compression threshold

When marking a serializable class with `CompressedJsonable` compression will only kick in when the serialized representation goes past a threshold. The default value for `akka.serialization.jackson.jackson-json-gzip.compress-larger-than` is 32 Kilobytes. As mentioned above, this setting was previously configure by `lagom.serialization.json.compress-larger-than` and defaulted to 1024 bytes. (See [#1983](https://github.com/lagom/lagom/pull/1983))
