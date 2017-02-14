# Development Environment

Lagom's sbt and Maven based development environments allow running any number of services together with a single command.

The same command also reloads your services when the code changes, so that you don't have to manually restart them. You can stay focused on your work and let Lagom do the compiling and reloading.

## Running all services in Maven

To run all services in Maven, simply run the `lagom:runAll` command:

```console
$ cd <path to your Lagom project>
$ mvn lagom:runAll
INFO ...
INFO Service hello-impl listening for HTTP on 0:0:0:0:0:0:0:0:23966
INFO Service hello-stream-impl listening for HTTP on 0:0:0:0:0:0:0:0:27462
(Services started, press enter to stop and go back to the console...)
```

This may take a while if you have a lot of services or if dependencies are being retrieved for the first time.

## Starting the sbt development console

Assuming you have have an sbt project, it's now time to fire up the console on your Lagom project directory:

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
[info] Service hello-impl listening for HTTP on 0:0:0:0:0:0:0:0:23966
[info] Service stream-impl listening for HTTP on 0:0:0:0:0:0:0:0:27462
(Services started, press enter to stop and go back to the console...)
```

This may take a while if you have a lot of services or if dependencies are being retrieved for the first time.

## Hot reloading

Once the "Services started" message has appeared, if you make a change to your source code, you'll see output like this in the console:

```console
[info] Compiling 1 Java source to /<project-path>/target/scala-2.11/classes...

--- (RELOAD) ---
```

## Behind the scenes

What's happening behind the scenes when you `runAll`?

* an embedded Service Locator is started
* a Cassandra server is started
* a Kafka server is started
* your services start
    * ...and register with the Service Locator

This all happens automatically without special code or additional configuration.

You can verify that your services are running by viewing `http://localhost:8000/services` in a web browser (or with a command line tool such as `curl`).  The Service Locator, running on port 8000, will return JSON such as:

```
[{"name":"hello-stream","url":"http://0.0.0.0:26230"},
 {"name":"cas_native","url":"tcp://127.0.0.1:4000/cas_native"},
 {"name":"hello","url":"http://0.0.0.0:24266"}]
```

`cas_native` is the [Cassandra](http://cassandra.apache.org/) server. As you will learn in the [[documentation for writing persistent and clustered services|PersistentEntity]], Cassandra is the default database in Lagom, and it's an integral part of the development environment.

The Service Locator, Cassandra, and Kafka are covered in more detail in the sections that follow.
