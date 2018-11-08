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

By default, all database files created by your running services are saved for the next time the Cassandra server is started. You can change the behaviour by adding the following in your build.

In the Maven root project pom:

```xml
<plugin>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-maven-plugin</artifactId>
    <version>${lagom.version}</version>
    <configuration>
        <cassandraCleanOnStart>true</cassandraCleanOnStart>
    </configuration>
</plugin>
```

In sbt:

@[cassandra-clean-on-start](code/build-cassandra-opts.sbt)


## Cassandra YAML configuration file

The Cassandra server can be configured with an alternative YAML file. By default, Lagom development environment uses [dev-embedded-cassandra.yaml](https://github.com/lagom/lagom/blob/master/dev/cassandra-server/src/main/resources/dev-embedded-cassandra.yaml). This is a good default to quickly get started, but if you find yourself needing to start Cassandra with a different configuration, you can easily do so by adding your own Cassandra YAML file to you to your build. 

In the Maven root project pom:

```xml
<plugin>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-maven-plugin</artifactId>
    <version>${lagom.version}</version>
    <configuration>
        <cassandraYamlFile>${basedir}/cassandra.yaml</cassandraYamlFile>
    </configuration>
</plugin>
```

In sbt:

@[cassandra-yaml-config](code/build-cassandra-opts.sbt)

Please note that the [Cassandra YAML file](https://github.com/lagom/lagom/blob/master/dev/cassandra-server/src/main/resources/dev-embedded-cassandra.yaml) used by Lagom has a few variables that are filled by some Lagom managed properties, namely: `$PORT` (defined by `lagomCassandraPort` in sbt or `cassandraPort` in mvn), `$STORAGE_PORT` (randomly defined) and `$DIR` (location for all Cassandra Server related files, defaults to: `target/embedded-cassandra`). It's not necessary to use these placeholders on your alternative YAML file, but it's recommended. Specially, the `$PORT` variable. If your YAML file has it hardcoded, you must make sure that Lagom will be using the same port (see [[Default port section|CassandraServer#Default-port]]).

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
         </cassandraJvmOptions>
    </configuration>
</plugin>
```

In sbt:

@[cassandra-jvm-options](code/build-cassandra-opts.sbt)

## Logging

Logging is configured such that it goes to the standard output, and the log level for `org.apache.cassandra` is set to `ERROR`. 

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

@[local-instance](code/build-cassandra-opts.sbt)

These two settings will only be used when running Lagom in DevMode. The purpose of these two settings is to disable the embedded Cassandra server and configure the Service Locator in DevMode to still be able to locate Cassandra when looking for `cas_native`. You may want to disable the Lagom-managed Cassandra server if you already have a Cassandra server running locally or in your company infrastructure and prefer using that. In that scenario it doesn't make sense for Lagom to start a Cassandra server and you will also gain few seconds of bootup time.

The service locator setup in these examples assumes your local Cassandra instance is running on port `9042`.
