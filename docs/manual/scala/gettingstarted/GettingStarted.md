# Creating and running Hello World with sbt

A Lagom system is typically made up of a set of sbt builds, with each build providing multiple services.  The easiest way to get started with a new Lagom system is to create a new project using the `lagom` sbt Giter8 template. This creates an sbt project, with two services, `hello` and `stream`. The template uses the `.sbtopts` file to increase the memory used by the `JVM` when starting your project. See [[Increase Memory in sbt|JVMMemoryOnDev]] for other ways to increase memory.

Follow these steps to create and run Hello World:

1. [Create the build](#create-the-build)
1. [Browse the build](#browse-the-build)
1. [Run Hello World](#run-hello-world)

## Create the build

To create a new build using the Giter8 template, open a console and enter the following:

```
sbt new lagom/lagom-scala.g8
```

The template prompts for the following parameters. Press `Enter` to accept the defaults or specify your own values:

* `name` - Becomes the name of the top-level directory.
* `organization` - Used as a package name.
* `version` - A version number for your system.
* `lagom-version` - The version number of Lagom, such as 1.3.0. We suggest that you use the latest official release.

## Browse the build

The created project contains the following elements:

```
hello                   → Project root
 └ hello-api            → hello api project
 └ hello-impl           → hello implementation project
 └ hello-stream-api     → hello-stream api project
 └ hello-stream-impl    → hello-stream implementation project
 └ project              → sbt configuration files
   └ build.properties   → Marker for sbt project
   └ plugins.sbt        → sbt plugins including the declaration for Lagom itself
 └ build.sbt            → Your project build file
```

* Notice how each service is broken up into two projects: api and implementation. The `api` project contains a service interface through which consumers may interact with the service. The `impl` project contains the actual service implementation.
* The `project` folder contains sbt-specific files.
* The `build.sbt` file contains all information necessary to build, run, and deploy your services.   


## Run Hello World

Lagom includes a development environment that let you start all your services by simply typing `runAll` in the sbt console. To run Hello World, change directories to the top-level directory and start sbt, when the command prompt displays, invoke `runAll`. For example:

```
cd hello
sbt
... (booting up)
> runAll
```
Among other messages, you should see the following:
```
[info] Starting embedded Cassandra server
..........
[info] Cassandra server running at 127.0.0.1:4000
[info] Service locator is running at http://localhost:8000
[info] Service gateway is running at http://localhost:9000
[info] Service hello-impl listening for HTTP on 0:0:0:0:0:0:0:0:24266
[info] Service hello-stream-impl listening for HTTP on 0:0:0:0:0:0:0:0:26230
(Services started, press enter to stop and go back to the console...)
```

Verify that the services are indeed up and running by invoking one of its endpoints from any HTTP client, such as a browser. The following request returns the message `Hello, World!`:

```
http://localhost:9000/api/hello/World
```



