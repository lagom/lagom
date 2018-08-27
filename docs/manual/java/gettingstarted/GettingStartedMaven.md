# Creating and running Hello World with Maven
The easiest way to get started with Lagom and Maven is to use the Maven archetype plugin and a Lagom archetype to create a new project.  A Maven project includes sub-projects organized in sub-directories. The top-level and sub-project directories each include a [Project Object Model (POM)](https://maven.apache.org/pom.html) that contains build configuration. When you run the `generate` command, Maven prompts you for POM element values. The Lagom archetype creates a project that includes two Lagom services, `hello` and `stream`.   

Follow these instructions to create and run your first project:

1. [Generate a project with the Lagom archetype](#Generate-a-project-with-the-Lagom-archetype)
1. [Browse the project structure](#Browse-the-project-structure)
1. [Run Hello World](#Run-Hello-World)

## Generate a project with the Lagom archetype

Choose a location on your file system for your Lagom projects. Maven will prompt you for a project name and will create a directory with that name that contains the build structure and Lagom example services. Note that it can take from a few seconds to a few minutes for Maven to download dependencies.

To create your project, follow these steps:

1. Open a console and change into the directory you selected for your project.
1. Invoke `mvn archetype:generate` from the command line:
    ```
    mvn archetype:generate -Dfilter=com.lightbend.lagom:maven-archetype-lagom-java
    ```
    Maven starts in interactive mode and prompts you to choose an archetype:
    ```
    Choose archetype:
    1: remote -> com.lightbend.lagom:maven-archetype-lagom-java (maven-archetype-lagom-java)
    Choose a number or apply filter (format: [groupId:]artifactId, case sensitive contains): :
    ```

1. Enter the number that corresponds with `com.lightbend.lagom:maven-archetype-lagom-java` (at time of writing, the number `1`, and the only one available).
    Maven prompts you for the version.
1. Enter the number corresponding with the version of Lagom you want to use. We recommend using the [current stable release](https://www.lagomframework.com/documentation/)).
    The template prompts you for POM values.
1. Specify values for:
    * `groupId`  - Usually a reversed domain name, such as `com.example.hello`.
    * `artifactId` - Maven also uses this value as the name for the top-level project folder. You might want to use a value such as `my-first-system`
    * `version` - Press `Enter` to accept the default or enter a version number for your project.
    * `package` - Press `Enter` to accept the default, which is the same as the `groupId`.  
    Maven prompts you to confirm POM values.    
1. Enter `Y` to accept the values.
   When finished, Maven creates the project, and completes with a message similar to the following:

```
   [INFO] ------------------------------------------------------------------------
   [INFO] BUILD SUCCESS
   [INFO] ------------------------------------------------------------------------
   [INFO] Total time: 10:42 min
   [INFO] Finished at: 2017-02-24T11:58:08-06:00
   [INFO] Final Memory: 17M/252M
   [INFO] ------------------------------------------------------------------------

```


## Browse the project structure

The structure for a project created with the Maven archetype generate command will look similar to the following (assuming `my-first-system` as an `artifactId`):

```
my-first-system
 └ hello-api/             → hello world api project dir
 └ hello-impl/            → hello world implementation dir
 └ integration-tests/
 └ stream-api/            → stream api project dir
 └ stream-impl/           → stream implementation project dir
 └ pom.xml                → Project group build file
```

Note that the `hello` and `stream` services each have:

* An `api` project that contains a service interface through which consumers can interact with the service.
* An `impl` project that contains the service implementation.

## Run Hello World

Lagom provides a `runAll` command to start the Lagom `hello` and `stream` services and runtime components, which include: Cassandra, Akka, and Kafka. From the top-level group directory, such as `my-first-system`, execute `lagom:runAll`.

For example:

```console
cd my-first-system
mvn lagom:runAll
```
It will take a bit of time for the services to start. The `Services started` message indicates the system is running:

```
...
[info] Starting embedded Cassandra server
..........
[info] Cassandra server running at 127.0.0.1:4000
[info] Service locator is running at http://localhost:9008
[info] Service gateway is running at http://localhost:9000
...
[INFO] Service hello-impl listening for HTTP on 127.0.0.1:65499
[INFO] Service hello-impl listening for HTTPS on 127.0.0.1:50695
[INFO] Service stream-impl listening for HTTP on 127.0.0.1:60212
[INFO] Service stream-impl listening for HTTPS on 127.0.0.1:58440
[INFO] (Services started, press enter to stop and go back to the console...)
```

Verify that the services are indeed up and running by invoking the `hello` service endpoint from any HTTP client, such as a browser:

```
http://localhost:9000/api/hello/World
```
The request returns the message `Hello, World!`.

Congratulations! You've created and run your first Lagom system.
