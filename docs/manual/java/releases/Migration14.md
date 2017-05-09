# Lagom 1.4 Migration Guide

This guide explains how to migrate from Lagom 1.3 to Lagom 1.4. If you are upgrading from an earlier version, be sure to review previous migration guides.

## Build changes

### Maven

If you're using a `lagom.version` property in the `properties` section of your root `pom.xml`, then simply update that to `1.4.0`. Otherwise, you'll need to go through every place that a Lagom dependency, including plugins, is used, and set the version there.

### sbt

The version of Lagom can be updated by editing the `project/plugins.sbt` file, and updating the version of the Lagom sbt plugin. For example:

```scala
addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.4.0")
```

## Binding services

Binding multiple Lagom service descriptors in one Lagom service has been deprecated. If you are currently binding multiple Lagom service descriptors in one Lagom service, you should combine these into one. The reason for this change is that we found most microservice deployment platforms simply don't support having multiple names for the one service, hence a service that serves multiple service descriptors, each with their own name, would not be compatible with those environments.

Consequently, we have deprecated the methods for binding multiple service descriptors. To migrate, in your Guice module that binds your services, change the following code:

```java
bindServices(serviceBinding(MyService.class, MyServiceImpl.class));
```

to:

```java
bindService(MyService.class, MyServiceImpl.class);
```

## Configuring Cassandra keyspaces

Lagom 1.4 requires each service that uses Cassandra persistence to define three keyspace configuration settings in `application.conf`:

```conf
cassandra-journal.keyspace = my_service_journal
cassandra-snapshot-store.keyspace = my_service_snapshot
lagom.persistence.read-side.cassandra.keyspace = my_service_read_side
```

While different services should be isolated by using different keyspaces, it is perfectly fine to use the same keyspace for all of these components within one service. In that case, it can be convenient to define a custom keyspace configuration property and use [property substitution](https://github.com/typesafehub/config#factor-out-common-values) to avoid repeating it.

```conf
my-service.cassandra.keyspace = my_service

cassandra-journal.keyspace = ${my-service.cassandra.keyspace}
cassandra-snapshot-store.keyspace = ${my-service.cassandra.keyspace}
lagom.persistence.read-side.cassandra.keyspace = ${my-service.cassandra.keyspace}
```

If you are using macOS or Linux, or have other access to a bash scripting environment, running this shell script in your base project directory can help add this configuration to existing services:

```bash
#!/bin/bash

set -eu

for svc_dir in *-impl; do
    svc=${svc_dir%-impl}

    # change $svc_dir to $svc on the next line to leave out the _impl suffix
    keyspace=$(tr - _ <<<$svc_dir)
    cat >> $svc_dir/src/main/resources/application.conf <<END
$svc.cassandra.keyspace = $keyspace

cassandra-journal.keyspace = \${$svc.cassandra.keyspace}
cassandra-snapshot-store.keyspace = \${$svc.cassandra.keyspace}
lagom.persistence.read-side.cassandra.keyspace = \${$svc.cassandra.keyspace}
END
done
```

Previous versions of Lagom automatically calculated a default Cassandra keyspace for each service, based on the name of the service project, and injected this keyspace configuration in development mode. When running in production, these calculated keyspaces were not used, resulting in multiple services sharing the same keyspaces by default.

In Lagom 1.4, services that use Cassandra persistence will fail on startup when these properties are not defined.

See [[Storing Persistent Entities in Cassandra|PersistentEntityCassandra#Configuration]] for more details.
