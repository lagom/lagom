# Kafka Server

By default, Lagom services that need to share information between each others use Kafka as a message broker. In a microservice architecture, usage of a message broker ensures that the services are not strongly coupled with each other. Therefore, for convenience, we have embedded a Kafka server in the development environment, so that you don't have to worry about installing it. There are a number of settings and tasks available to tune the Kafka server to your liking, let's explore them:

## Default port

By default, the [Kafka](https://kafka.apache.org/) server is started on port `9092`. Kafka uses [ZooKeeper](https://zookeeper.apache.org/), and hence a ZooKeeper server is also started on port `2181`. If the current default ports don't suit you, you can change either by adding the following in your build.

In the Maven root project pom:

```xml
<plugin>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-maven-plugin</artifactId>
    <version>${lagom.version}</version>
    <configuration>
        <kafkaPort>10000</kafkaPort>
        <zookeeperPort>9999</zookeeperPort>
    </configuration>
</plugin>
```

In sbt:

@[kafka-port](code/build-kafka-opts.sbt)

## Kafka properties file

The Kafka server can be configured with an alternative property file. By default, Lagom development environment uses a stock [kafka-server.properties](https://github.com/lagom/lagom/blob/master/dev/kafka-server/src/main/resources/kafka-server.properties) file provided with Kafka, with only one change to allow auto creation of topics on the server. This is a good default to quickly get started, but if you find yourself needing to start Kafka with a different configuration, you can easily do so by adding your own Kafka `kafka-server.properties` file to you to your build.

In the Maven root project pom:

```xml
<plugin>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-maven-plugin</artifactId>
    <version>${lagom.version}</version>
    <configuration>
        <kafkaPropertiesFile>${basedir}/kafka-server.properties</kafkaPropertiesFile>
    </configuration>
</plugin>
```

In sbt:

@[kafka-properties](code/build-kafka-opts.sbt)

## JVM options

The Kafka server is run on a separate process, and a JVM is started with sensible memory defaults. However, if the default JVM options don't suit you, you can override them by adding the following in your build.

In the Maven root project pom:

```xml
<plugin>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-maven-plugin</artifactId>
    <version>${lagom.version}</version>
    <configuration>
        <kafkaJvmOptions>
             <opt>-Xms256m</opt>
             <opt>-Xmx1024m</opt>
         </kafkaJvmOptions>
    </configuration>
</plugin>
```

In sbt:

@[kafka-jvm-options](code/build-kafka-opts.sbt)

## Logging

Logging is configured such that it goes *only* to files. You can find the logs of Kafka in the folder `<your-project-root>/target/lagom-dynamic-projects/lagom-internal-meta-project-kafka/target/log4j_output`.

## Commit Log

Kafka is essentially a durable commit log. You can find all data persisted by Kafka in the folder `<your-project-root>/target/lagom-dynamic-projects/lagom-internal-meta-project-kafka/target/logs`

## Start and stop

The Kafka server is automatically started when executing the `runAll` task. However, there are times when you might want to manually start only a few services, and hence you won't use the `runAll` task. In this case, you can manually start the Kafka server via the `lagom:startKafka` maven task or `lagomKafkaStart` sbt task, and stopping it with the `lagom:stopKafka` Maven task or `lagomKafkaStop` sbt task.

## Disable it

You can disable the embedded Kafka server by adding the following in your build.

In the Maven root project pom:

```xml
<plugin>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-maven-plugin</artifactId>
    <version>${lagom.version}</version>
    <configuration>
        <kafkaEnabled>false</kafkaEnabled>
    </configuration>
</plugin>
```

In sbt:

@[kafka-enabled](code/build-kafka-opts.sbt)

One good reason to disable the embedded Kafka server is if you need your services to connect to an external Kafka instance.

## Connecting to an external Kafka server

It's possible to connect to an external Kafka server in place of the embedded one. All you need to do is adding the following in your build.

In the Maven root project pom:

```xml
<plugin>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-maven-plugin</artifactId>
    <version>${lagom.version}</version>
    <configuration>
        <kafkaAddress>localhost:10000</kafkaAddress>
        <kafkaEnabled>false</kafkaEnabled>
    </configuration>
</plugin>
```

In sbt:

@[external-instance](code/build-kafka-opts.sbt)

As you have probably noticed, the above configured Kafka server is actually running locally (mind the *localhost* in the provided address). In this case, it would have actually been enough to configure the port on which is running, without having to provide the full address.

```xml
<plugin>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-maven-plugin</artifactId>
    <version>${lagom.version}</version>
    <configuration>
        <kafkaPort>10000</kafkaPort>
        <kafkaEnabled>false</kafkaEnabled>
    </configuration>
</plugin>
```

In sbt:

@[local-instance](code/build-kafka-opts.sbt)

Assuming your local Kafka instance is running on port `10000`.
