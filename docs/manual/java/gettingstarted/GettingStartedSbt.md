# Creating and running Hello World with sbt

A Lagom system is typically made up of a set of sbt builds, with each build containing multiple services. The easiest way to get started with sbt is to create a new build using the sbt Giter8 `lagom` template.

The `lagom-java` template uses the `.sbtopts` file to increase the memory used by the `JVM` when starting your project. See [[Increase Memory in sbt|JVMMemoryOnDev]] for other ways to configure memory usage.

Follow these steps to create your first Lagom build:

1. [Create a new Lagom build](#Create-a-new-Lagom-build)
1. [Browse the build](#Browse-the-build)
1. [Run Hello World](#Run-Hello-World)

## Create a new Lagom build

Choose a location on your file system for your Lagom projects. The template will prompt you for a project name and will create a directory with that name that contains the build structure and Lagom example services. Note that it can take from a few seconds to a few minutes for sbt to download dependencies.

To create your project, follow these steps:


1. Open a console and change into the directory you selected for your project.

1. Enter the following command to invoke `sbt new` using the Lagom Giter8 template:
   ```
   sbt new lagom/lagom-java.g8
   ```
1. The sbt Lagom template prompts for the following parameters. Press `Enter` to accept the defaults or specify your own values:

* `name` - Becomes the name of the top-level directory.
* `organization` - Used as a package name.
* `version` - A version number for your system.
* `lagom-version` - The version number of Lagom, such as 1.3.2. Be sure to use the [current stable release](https://www.lagomframework.com/documentation/).

## Browse the build

The build created by the template contains the following elements:

```
hello                   → Project root
 └ hello-api            → hello api project
 └ hello-impl           → hello implementation project
 └ hello-stream-api     → hello-stream api project
 └ hello-stream-impl    → hello-stream implementation project
 └ project              → sbt configuration files
   └ build.properties   → Marker for sbt project
   └ plugins.sbt        → sbt plugins including the declaration for Lagom itself
 └ build.sbt            → Build configuration
```

* Notice how each service is broken up into two projects: api and implementation. The api project contains a service interface through which consumers may interact with the service, while the `impl` project contains the actual service implementation.
* The `project` folder contains sbt specific files.
* The `build.sbt` file contains all information necessary to build, run, and deploy your services.


## Run Hello World

Lagom includes a development environment that let you start all your services by simply typing `runAll` in the sbt console. From a console change directories to the top-level directory and start sbt, when the command prompt displays, invoke `runAll`:

```
cd hello
sbt
... (booting up)
> runAll
```
It will take a bit of time to build the project and start the services. Among other messages, you should see the following:
```
[info] Starting embedded Cassandra server
..........
[info] Cassandra server running at 127.0.0.1:4000
[info] Service locator is running at http://localhost:9008
[info] Service gateway is running at http://localhost:9000
[info] Service helloworld-impl listening for HTTP on 127.0.0.1:24266
[info] Service helloworld-impl listening for HTTPS on 127.0.0.1:50695
[info] Service hellostream-impl listening for HTTP on 127.0.0.1:26230
[info] Service hellostream-impl listening for HTTPS on 127.0.0.1:58440
(Services started, press enter to stop and go back to the console...)
```

You can verify that the services are indeed up and running by invoking a service endpoint from any HTTP client, such as a browser:

```
http://localhost:9000/api/hello/World
```

The service returns the message, `Hello, World!`. Congratulations! You've created and run your first Lagom system.
