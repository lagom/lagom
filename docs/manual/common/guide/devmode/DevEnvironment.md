# Development Environment

Lagom's sbt and Maven based development environments allow running any number of services together with a single command.

The same command also reloads your services when the code changes, so that you don't have to manually restart them. You can stay focused on your work and let Lagom do the compiling and reloading.

## Running all services in Maven

To run all services in Maven, simply run the `lagom:runAll` command:

```console
$ cd <path to your Lagom project>
$ mvn lagom:runAll
INFO ...
INFO Service hello-impl listening for HTTP on 127.0.0.1:24266
INFO Service hello-impl listening for HTTPS on 127.0.0.1:50695
INFO Service hello-stream-impl listening for HTTP on 127.0.0.1:26230
INFO Service hello-stream-impl listening for HTTPS on 127.0.0.1:58440
(Services started, press enter to stop and go back to the console...)
```

This may take a while if you have a lot of services or if dependencies are being retrieved for the first time.

## Starting the sbt development console

Assuming you have an sbt project, it's now time to fire up the console on your Lagom project directory:

```console
$ cd <path to your Lagom project>
$ sbt
[info] ...
>
```

To run all Lagom services together, with automatic reloading, just enter `runAll` in the sbt console:

```console
> runAll
[info] ...
[info] Service hello-impl listening for HTTP on 127.0.0.1:24266
[info] Service hello-impl listening for HTTPS on 127.0.0.1:50695
[info] Service stream-impl listening for HTTP on 127.0.0.1:26230
[info] Service stream-impl listening for HTTPS on 127.0.0.1:58440
(Services started, press enter to stop and go back to the console...)
```

This may take a while if you have a lot of services or if dependencies are being retrieved for the first time.

## Hot reloading

Once the "Services started" message has appeared, if you make a change to your source code, you'll see output like this in the console:

```console
[info] Compiling 1 Java source to /<project-path>/target/scala-2.12/classes...

--- (RELOAD) ---
```

## Managing custom services

By default, Lagom will, in addition to running your services, also start a service locator, a Cassandra server and a Kafka server. If using sbt, you can customize what Lagom starts, including adding other databases and infrastructure services.

> **Note:** Managing custom services is not currently supported in Maven, due to Maven's inability to arbitrarily add behaviour, such as the logic necessary to start and stop an external process, to a build. This is typically not a big problem, it simply means developers have to manually install, start and stop these services themselves.

To add a custom service, first you need to define a task to start the service in your `build.sbt`. The task should produce a result of `Closeable`, which can be used to stop the service. Here's an example for Elastic Search:

@[start-elastic-search](code/dev-environment.sbt)

Now we're able to start Elastic Search, we need to add this task to Lagom's list of infrastructure services, so that Lagom will start it when `runAll` is executed. This can be done by modifying the `lagomInfrastructureServices` setting:

@[infrastructure-services](code/dev-environment.sbt)

## Behind the scenes

<!-- copied this section to concepts, perhaps it can be removed later -->
What's happening behind the scenes when you `runAll`?

* an embedded [[Service Locator|ServiceLocator]] is started
* an embedded [[Service Gateway|ServiceLocator#Service-Gateway]] is started
* a [[Cassandra server|CassandraServer]] is started
* a [[Kafka server|KafkaServer]] is started
* your services start
    * ...and register with the Service Locator
    * ...and register the publicly accessible paths in the Service Gateway

This all happens automatically without special code or additional configuration.

<!--end copied section -->

You can verify that your services are running by viewing `http://localhost:9008/services` in a web browser (or with a command line tool such as `curl`). The Service Locator, running on port `9008`, will return JSON such as:

```
[
  {
    "name":"cas_native",
    "url":"tcp://127.0.0.1:4000/cas_native",
    "portName":null
  },
  {
    "name":"kafka_native",
    "url":"tcp://localhost:9092/kafka_native",
    "portName":null
  },
  {
    "name":"hello",
    "url":"http://127.0.0.1:65499",
    "portName":null
  },
  {
    "name":"hello",
    "url":"http://127.0.0.1:65499",
    "portName":"http"
    }
]
```

`cas_native` is the [Cassandra](https://cassandra.apache.org/) server. As you will learn in the [[documentation for writing persistent and clustered services|PersistentEntity]], Cassandra is the default database in Lagom, and it's an integral part of the development environment.

The Service Locator, Cassandra, and Kafka are covered in more detail in the sections that follow.
