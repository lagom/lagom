# Cassandra Server

By default, Lagom services needing to persist data use Cassandra as database. For convenience, we have embedded a Cassandra server in the development environment, so that you don't have to worry about installing it. There are a number of settings and tasks available to tune the Cassandra server to your liking, let's explore them:

## Default port

By default, the Cassandra server is started on port `4000`. We are aware that Cassandra is usually run on port `9042`, and that is precisely the reason why we picked a different port: we do not want to interfere with your locally running Cassandra, if you happen to have one. If the current default port doesn't suit you, and for instance you would prefer to have the embedded Cassandra server running on port `9042`, you can do so by adding the following in your build.

In the Maven root project pom:

```xml
<plugin>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-maven-plugin</artifactId>
    <version>${lagom.version}</version>
    <configuration>
        <cassandraPort>9042</cassandraPort>
    </configuration>
</plugin>
```

In sbt:

@[cassandra-port](code/build-cassandra-opts.sbt)

## Clean up on start

By default, all database files created by your running services are going to be deleted the next time the Cassandra server is started. You can turn off this feature by adding the following in your build.

In the Maven root project pom:

```xml
<plugin>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-maven-plugin</artifactId>
    <version>${lagom.version}</version>
    <configuration>
        <cassandraCleanOnStart>false</cassandraCleanOnStart>
    </configuration>
</plugin>
```

In sbt:

@[cassandra-clean-on-start](code/build-cassandra-opts.sbt)

## JVM options

The Cassandra server is run on a separate process, and a JVM is started with sensible memory defaults. However, if the default JVM options don't suit you, you can override them by adding the following in your build.

In the Maven root project pom:

```xml
<plugin>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-maven-plugin</artifactId>
    <version>${lagom.version}</version>
    <configuration>
        <cassandraJvmOptions>
             <opt>-Xms256m</opt>
             <opt>-Xmx1024m</opt>
             <opt>-Dcassandra.jmx.local.port=4099</opt>
             <opt>-DCassandraLauncher.configResource=dev-embedded-cassandra.yaml</opt>
         </cassandraJvmOptions>
    </configuration>
</plugin>
```

In sbt:

@[cassandra-jvm-options](code/build-cassandra-opts.sbt)

## Yaml configuration

As shown above, the YAML configuration file can be configured by modifying the Cassandra JVM options to include a `-DCassandraLauncher.configResource` system property that points to a resource in your `src/main/resources` directory.

## Logging

Logging is configured such that it goes to the standard output, and the log level for `org.apache.cassandra` is set to `ERROR`. Here is the used `logback.xml` file:

@[](../../../../../dev/cassandra-server/src/main/resources/logback.xml)

There is no mechanism in place to edit the used `logback.xml`. If you need to tune the logging configuration, you should install Cassandra, and [[read the instructions|CassandraServer#Connecting-to-a-locally-running-Cassandra-instance]] to connect to a locally running Cassandra.

## Cassandra start time

[[As mentioned|DevEnvironment]], the `runAll` task also takes care of starting the embedded Cassandra server, before starting any other service. Moreover, services are usually started only after the Cassandra server is reachable. By default, the Cassandra server is given up to 20 seconds to be up and running, but you can change this default by adding the following in your build.

In the Maven root project pom:

```xml
<plugin>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-maven-plugin</artifactId>
    <version>${lagom.version}</version>
    <configuration>
        <cassandraMaxBootWaitingSeconds>0</cassandraMaxBootWaitingSeconds>
    </configuration>
</plugin>
```

In sbt:

@[cassandra-boot-waiting-time](code/build-cassandra-opts.sbt)

Changing the Cassandra server maximum boot waiting time to be 0 can be useful to emulate a real-world deployment scenario, since a running Cassandra instance may not be available the moment a service is started.

## Start and stop

The Cassandra server is automatically started when executing the `runAll` task. However, there are times when you might want to manually start only a few services, and hence you won't use the `runAll` task. In this case, you can manually start the Cassandra server via the `lagom:startCassandra` maven task or `lagomCassandraStart` sbt task, and stopping it with the `lagom:stopCassandra` Maven task or `lagomCassandraStop` sbt task.

## Disable it

You can disable the embedded Cassandra server by adding the following in your build.

In the Maven root project pom:

```xml
<plugin>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-maven-plugin</artifactId>
    <version>${lagom.version}</version>
    <configuration>
        <cassandraEnabled>false</cassandraEnabled>
    </configuration>
</plugin>
```

In sbt:

@[cassandra-enabled](code/build-cassandra-opts.sbt)

One good reason to disable the embedded Cassandra server is if you need your services to connect to an external, locally running, Cassandra instance.

## Connecting to a locally running Cassandra instance

It's possible to connect to an [[externally run|ServiceLocator#Communicating-with-external-services]] Cassandra instance in place of the embedded one. All you need to do is adding the following in your build.

In the Maven root project pom:

```xml
<plugin>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-maven-plugin</artifactId>
    <version>${lagom.version}</version>
    <configuration>
        <unmanagedServices>
            <cas_native>http://localhost:9042</cas_native>
        </unmanagedServices>
        <cassandraEnabled>false</cassandraEnabled>
    </configuration>
</plugin>
```

In sbt:

@[local-instance](code/build-cassandra-opts3.sbt)

These two settings will only be used when using Lagom in DevMode. The purpose of these two settings is to disabled the embedded Cassandra server and configure the Service Locator in DevMode to still be able to locate Cassandra when looking for `cas_native`.

The service locator setup in these examples assumes your local Cassandra instance is running on port `9042`.

## Disabling Service Location

**DISCLAIMER** The following is unrecommended. Prefer [[Service Location|ServiceDiscovery]] over static lists of endpoints.

It is possible to hardcode the list of `contact-points` where Cassandra may be located. That is the default behavior in `akka-persistence-cassandra` but Lagom overrides that behavior implementing a Session provider based on service location. That allows all services to continue to operate without the need to redeploy if/when the Cassandra `contact-points` are updated or fail. Using a Service Location based approach provides higher resiliency.

Despite that, there scenarios where a hard-coded list of contact-points is required and updating and maintaining a list of endpoints in a service locator is not a viable option. In that case a user may use the following setup:

```
cassandra {
  ## list the contact points  here
  contact-points = ["10.0.1.71", "23.51.143.11"]
  ## override Lagom’s ServiceLocator-based ConfigSessionProvider
  session-provider = akka.persistence.cassandra.ConfigSessionProvider
}
```
