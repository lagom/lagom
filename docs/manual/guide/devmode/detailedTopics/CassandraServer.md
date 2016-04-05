# Cassandra Server

By default, Lagom services needing to persist data use Cassandra as database. Therefore, for conveniency, we have embedded a Cassandra server in the development environment, so that you don't have to worry about installing it. There are a number of settings and tasks available to tune the Cassandra server to your liking, let's explore them:

## Default port

By default, the Cassandra server is started on port `4000`. We are aware that Cassandra is usually run on port `9042`, and that is precisely the reason why we picked a different port: we do not want to interfere with your locally running Cassandra, if you happen to have one. If the current default port doesn't suit you, and for instance you would prefer to have the embedded Cassandra server running on port `9042`, you can do so by adding the following in your build:

@[cassandra-port](code/build-cassandra-opts.sbt)

## Clean up on start

By default, all database files created by your running services are going to be deleted the next time the Cassandra server is started. You can turn off this feature by adding the following in your build:

@[cassandra-clean-on-start](code/build-cassandra-opts.sbt)

# Keyspace

A keyspace in Cassandra is a namespace that defines data replication on nodes. Each service should use a unique keyspace name so that the tables of different services are not conflicting with each other. But don't worry, we have already taken care of that and, by default, the keyspace is automatically set to be the project's name (after possibly having replaced a few characters that aren't allowed). If the generated keyspace doesn't suit you, you are free to provide a custom one. Let's assume you have the following Lagom project in your build:

@[cassandra-users-project](code/build-cassandra-opts.sbt)

Because the project's name is `users-impl`, the generated Cassandra keyspace will be `users_impl` (note that dashes are replaced with underscores). If you'd prefer the keyspace to be named simply `users`, you could either change the project's `name` to be `users`, or alternatively add the following setting:

@[cassandra-users-project](code/build-cassandra-opts2.sbt)

It is worth pointing out that, despite the above, a Cassandra keyspace will still need to be provided when running your service in production. Hence, if you'd like to provide a Cassandra keyspace name that can be used both in development and production, it is recommended to do so via a configuration file.

For instance, instead of setting the keyspace using the `lagomCassandraKeyspace` as we did before, we can obtain the same result by adding the following additional keys/values in the project's `application.conf` (note that if you do not have an `application.conf`, you should create one. For the above defined project, it would be typically placed under `usersImpl/src/main/resources/`):

```
cassandra-journal.keyspace=users
cassandra-snapshot-store.keyspace=users
lagom.persistence.read-side.cassandra.keyspace=users
```

Note that the keyspace values provided via the `application.conf` will always win over any keyspace name that may be set in the build.

## JVM options

The Cassandra server is run on a separate process, and a JVM is started with sensible memory defaults. However, if the default JVM options don't suit you, you can override them by adding the following in your build:

@[cassandra-jvm-options](code/build-cassandra-opts.sbt)

## Yaml configuration

You can provide a custom `.yaml` configuration to use when starting the embedded Cassandra server via the `-DCassandraLauncher.configResource` system property. Just add the following in your build:

@[cassandra-yaml](code/build-cassandra-opts2.sbt)

## Logging

Logging is configured such that it goes to the standard output, and the log level for `org.apache.cassandra` is set to `ERROR`. Here is the used `logback.xml` file:

@[](../../../../../dev/cassandra-server/src/main/resources/logback.xml)

There is no mechanism in place to edit the used `logback.xml`. If you need to tune the logging configuration, you should install Cassandra, and [[read the instructions|CassandraServer#Connecting-to-a-locally-running-Cassandra-instance]] to connect to a locally running Cassandra. 

## Cassandra start time

[[As mentioned|DevEnvironment#Cassandra]], the `runAll` task also takes care of starting the embedded Cassandra server, before starting any other service. Moreover, services are usually started only after the Cassandra server is reachable. By default, the Cassandra server is given up to 20 seconds to be up and running, but you can change this default by adding the following in your build:

@[cassandra-boot-waiting-time](code/build-cassandra-opts.sbt)

Changing the Cassandra server maximum boot waiting time to be `0.seconds` can be useful to emulate a real-world deployment scenario, since a running Cassandra instance may not be available the moment a service is started.

## Start and stop

The Cassandra server is automatically started when executing the `runAll` task. However, there are times when you might want to manually start only a few services, and hence you won't use the `runAll` task. In this case, you can manually start the Cassandra server via the `lagomCassandraStart` task, and stopping it with the `lagomCassandraStop` task.

## Disable it

You can disable the embedded Cassandra server by adding the following in your build:

@[cassandra-enabled](code/build-cassandra-opts.sbt)

One good reason to disable the embedded Cassandra server is if you need your services to connect to an external, locally running, Cassandra instance.

## Connecting to a locally running Cassandra instance

It's possible to connect to a locally running Cassandra instance in place of the embedded one. All you need to do is adding the following in your build:

@[local-instance](code/build-cassandra-opts3.sbt)

Assuming your local Cassandra instance is running on port `9042`.
