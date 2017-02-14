# Creating and running Hello World with Maven
The easiest way to get started with Lagom and Maven is to use the Maven archetype plugin and a Lagom archetype to create a new project.  A Maven project includes sub-projects organized in sub-directories. The top-level and sub-project directories each include a [Project Object Model (POM)](https://maven.apache.org/pom.html) that contains build configuration. When you run the `generate` command, Maven prompts you for POM element values. The Lagom archetype creates a project that includes two Lagom services, `hello` and `stream`.   

Follow these instructions to create and run your first project:

1. [Generate a project with the Lagom archetype](#generate-a-project-with-the-lagom-archetype)
1. [Browse the project structure](#browse-the-project-structure)
1. [Run Hello World](#run-hello-world)

## Generate a project with the Lagom archetype

To create a project, invoke `mvn archetype:generate` from the command line with the following parameters (all on one line). The `archetypeVersion` parameter specifies the Lagom version (in this example, 1.3.0).  

```
mvn archetype:generate -DarchetypeGroupId=com.lightbend.lagom -DarchetypeArtifactId=maven-archetype-lagom-java
-DarchetypeVersion=1.3.0
```

When the template prompts you for POM values, accept defaults by pressing `Enter` or specify your own for:

* `groupId`  - Usually a reversed domain name, such as `com.example.hello`.
* `artifactId` - Maven also uses this value as the name for the top-level project folder. You might want to use a value such as `my-first-system`
* `version` - A version number for your project.
* `package` - By default, the same as the `groupId`.  
> **Note:** Be sure to use the latest version of Lagom for the `archetypeVersion` parameter.

## Browse the project structure

The structure for a project created with the Maven archetype generate command will look similar to the following (assuming `my-first-system` as an `artifactId`):

```
my-first-system 
 └ cassandra-config      
 └ hello-api             → hello world api project dir
 └ hello-impl            → hello world implementation dir 
 └ integration-tests    
 └ stream-api            → stream api project dir
 └ stream-impl           → stream implementation project dir
 
 └ pom.xml               → Project group build file
```

Note that the `hello` and `stream` services each have: 

* An `api` project that contains a service interface through which consumers can interact with the service. 
* An `impl` project that contains the service implementation.

## Run Hello World

Lagom provides a `runAll` command to start the Lagom `hello` and `stream` services and runtime components, which include: Cassandra, Akka, and Kafka. From the top-level group directory, such as `my-first-system`, execute `lagom:runAll` (some console output omitted for brevity):

```console
cd my-first-system
mvn lagom:runAll
...
[info] Starting embedded Cassandra server
..........
[info] Cassandra server running at 127.0.0.1:4000
[info] Service locator is running at http://localhost:8000
[info] Service gateway is running at http://localhost:9000
...
[info] Service hello-impl listening for HTTP on 0:0:0:0:0:0:0:0:24266
[info] Service stream-impl listening for HTTP on 0:0:0:0:0:0:0:0:26230
(Services started, press enter to stop and go back to the console...)
```

Verify that the services are indeed up and running by invoking the `hello` service endpoint from any HTTP client, such as a browser: 

```
http://localhost:9000/api/hello/World
```
The request returns the message `Hello, World!`.

Congratulations! You've created and run your first Lagom system. 
