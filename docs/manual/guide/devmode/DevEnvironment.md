# Development Environment

Lagom's sbt-based development console allows running any number of services together with a single command.

The same command also reloads your services when the code changes, so that you don't have to manually restart them. You can stay focused on your work and let Lagom do the compiling and reloading.

## Starting the development console

Assuming you have followed the [[Installation|Installation]] instructions, it's now time to fire up the console on your Lagom project directory:

```console
$ cd <path to your Lagom project>
$ activator
[info] ...
>
```

## Running all services

To run all Lagom services together, with automatic reloading, just enter `runAll` in the activator console:

```console
> runAll
[info] ...
[info] Service helloworld-impl listening for HTTP on 0:0:0:0:0:0:0:0:23966
[info] Service hellostream-impl listening for HTTP on 0:0:0:0:0:0:0:0:27462
(Services started, use Ctrl+D to stop and go back to the console...)
```

This may take a while if you have a lot of services or if dependencies are being retrieved for the first time.

Once the "Services started" message has appeared, if you make a change to your source code, you'll see output like this in the console:

```console
[info] Compiling 1 Java source to /<project-path>/target/scala-2.11/classes...

--- (RELOAD) ---
```

## Behind the scenes

What's happening behind the scenes when you `runAll`?

* an embedded Service Locator is started
* a Cassandra server is started
* your services start
    * ...and register with the Service Locator

This all happens automatically without special code or additional configuration.

You can verify that your services are running by viewing `http://localhost:8000/services` in a web browser (or with a command line tool such as `curl`).  The Service Locator, running on port 8000, will return JSON such as:

```
[{"name":"hellostream","url":"http://0.0.0.0:26230"},
 {"name":"cas_native","url":"tcp://127.0.0.1:4000/cas_native"},
 {"name":"helloservice","url":"http://0.0.0.0:24266"}]
```

`cas_native` is the [Cassandra](http://cassandra.apache.org/) server. As you will learn in the [[documentation for writing persistent and clustered services|PersistentEntity]], Cassandra is the default database in Lagom, and it's an integral part of the development environment.

Both the Service Locator and Cassandra are covered in more detail in the sections that follow.
