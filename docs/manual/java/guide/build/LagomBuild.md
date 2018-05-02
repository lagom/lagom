# Defining a Lagom build

As already discussed in [[Lagom build philosophy|BuildConcepts]], with Lagom you are free to combine all your services in a single build, or build them individually.

Below, we describe how to make a single build containing all your services.  The `hello` sample follows this structure.

Then, in the next section, we'll describe the alternative approach of one build per service.

## Understanding your project structure

Every service contains at least two parts: an API project and an implementation project. (These are subprojects within the same build.)

The API project contains the service interface, also known as the descriptor, along with all the data models that the interface uses, e.g. request and response messages.  The API project can be depended on and consumed by other services.

The implementation project will naturally also depend on the API project, in order to implement it.

Consider the sample system below:

![Lagom project structure](resources/guide/build/lagom-project-structure.png)

This system has two services, one called `hello`, and one called `hello-stream`.  Each service has two sbt projects defined, an API project, `hello-api` and `hello-stream-api`, and an implementation project, `hello-impl` and `hello-stream-impl`.  Additionally, `hello-stream-impl` depends on `hello-api`, and uses that to invoke calls on `hello`.

* [Defining a build in Maven](#Defining-a-build-in-Maven)
* [Defining a build in sbt](#Defining-a-build-in-sbt)

## Defining a build in Maven

### Configuring the root project

We recommend the usage of maven properties to define the Lagom Version and Scala Binary Version to use. The properties should be configured in the root project under `<project>` tag:

```xml
<properties>
    <scala.binary.version>2.12</scala.binary.version>
    <lagom.version>1.4.3</lagom.version>
</properties>
```

In Lagom, it is typical to use a multi module build. The Lagom maven plugin will generally be configured in the root project, which can be done by adding it to the `<plugins>` section of your `pom.xml`:

```xml
<plugin>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-maven-plugin</artifactId>
    <version>${lagom.version}</version>
</plugin>
```

Doing this will allow you to use tasks like `lagom:runAll` to run every service in the system, and to define system wide configuration. Maven plugins inherit their configuration from parent poms, so whatever you define in the parent pom will be used for all services.

Since Lagom requires JDK 8, you will need to set the source and target versions for Java compilation to be 1.8. Additionally, Lagom comes with the Jackson parameter names extension out of the box, allowing Jackson to deserialize json into immutable classes with no additional annotation metadata. To take advantage of this feature, the Java compiler must have parameter names enabled. The source, target and parameter names configuration is best configured in the root project, since the configuration will be inherited by all child modules:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.5.1</version>
    <configuration>
        <source>1.8</source>
        <target>1.8</target>
        <compilerArgs>
            <arg>-parameters</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

We also recommend using Maven dependency management in your root project pom to control the versions of your dependencies across the whole system. For example:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.lightbend.lagom</groupId>
            <artifactId>lagom-javadsl-server_${scala.binary.version}</artifactId>
            <version>${lagom.version}</version>
        </dependency>
        <dependency>
            <groupId>com.typesafe.play</groupId>
            <artifactId>play-akka-http-server_${scala.binary.version}</artifactId>
            <version>${play.version}</version>
        </dependency>
        <dependency>
            <groupId>com.lightbend.lagom</groupId>
            <artifactId>lagom-javadsl-api_${scala.binary.version}</artifactId>
            <version>${lagom.version}</version>
        </dependency>
        ...
    </dependencies>
</dependencyManagement>
```

#### A note on Scala versions

When adding dependencies to Lagom libraries, you need to ensure that you include the Scala major version in the artifact ID, for example, `lagom-javadsl-api_${scala.binary.version}`. Note, `${scala.binary.version}` references a maven property as explained above.

Lagom itself is implemented mostly in Scala. Unlike Java, where the Java maintainers control the virtual machine and so can build backwards compatibility into the virtual machine when new features are added, when new features are added in Scala, backwards compatibility is very difficult if not impossible to maintain. Therefore, libraries have to be compiled against a particular major version of Scala.

Often libraries will want to support multiple versions of Scala, doing this requires building one artifact for each version of Scala that they support, which introduces the problem of how to differentiate between those artifacts, seeing as maven doesn't support the idea of adding additional metadata to dependencies to specify what version of Scala they require. To solve this, the convention of appending the Scala version to the artifact is used.

### Defining a service

The API module for a service is a simple maven project.  It doesn't need to configure the Lagom plugin, often it will need no more than a dependency on the Lagom API library.  For example:

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.example</groupId>
        <artifactId>my-first-system</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>hello-api</artifactId>

    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.lightbend.lagom</groupId>
            <artifactId>lagom-javadsl-api_${scala.binary.version}</artifactId>
        </dependency>
    </dependencies>
</project>
```

The implementation module for a service is also a simple maven project, but will have a few more dependencies, and will need to configure the `lagom-maven-plugin` to mark itself as a service project, so that the plugin knows to include it when `lagom:runAll` is used:

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.example</groupId>
        <artifactId>my-first-system</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>hello-impl</artifactId>

    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>${project.groupId}</groupId>
            <artifactId>hello-api</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.lightbend.lagom</groupId>
            <artifactId>lagom-javadsl-server_${scala.binary.version}</artifactId>
        </dependency>
        <dependency>
            <groupId>com.lightbend.lagom</groupId>
            <artifactId>lagom-javadsl-persistence_${scala.binary.version}</artifactId>
        </dependency>
        <dependency>
            <groupId>com.lightbend.lagom</groupId>
            <artifactId>lagom-logback_${scala.binary.version}</artifactId>
        </dependency>
        <dependency>
            <groupId>com.typesafe.play</groupId>
            <artifactId>play-akka-http-server_${scala.binary.version}</artifactId>
        </dependency>
        <dependency>
            <groupId>com.lightbend.lagom</groupId>
            <artifactId>lagom-javadsl-testkit_${scala.binary.version}</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>com.lightbend.lagom</groupId>
                <artifactId>lagom-maven-plugin</artifactId>
                <configuration>
                    <lagomService>true</lagomService>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

A few things to notice here are:

* The implementation depends on its API project, so that it can implement that API.
* It also has a dependency on `lagom-javadsl-server`, this provides all server side code for the project.
* This particular service uses the Lagom persistence API to persist data, and so has a dependency on `lagom-javadsl-persistence`.
* A logging implementation needs to be configured, this uses the default `lagom-logback` logging implementation.
* An implementation of the Play HTTP server needs to be configured - Play provides two server implementations, one in Netty, and one in Akka HTTP.  In this case, Akka HTTP has been selected. Replace `play-akka-http-server_${scala.binary.version}` with `play-netty-server_${scala.binary.version}` if you wanted to switch.
* The `lagom-maven-plugin` has been configured to say that `lagomService` is `true`, this tells Lagom that this is a lagom service that should be run when you run `lagom:runAll`.

## Defining a build in sbt

### Adding the Lagom sbt plugin

A Lagom build must tell sbt to use the Lagom plugin.  This is done by creating a file called `project/plugins.sbt`, and adding the plugin like so:

@[add-sbt-plugin](code/lagom-build.sbt)

The plugin provides all the necessary support for building, running, and deploying your Lagom application.

For more information on sbt plugins, see the sbt documentation on [Using Plugins](https://www.scala-sbt.org/1.x/docs/Using-Plugins.html).

### Defining a build

An sbt build is defined in one or more `*.sbt` files in the build's root directory.  It's conventional to have a single file named `build.sbt`; you can split it into multiple files later if it becomes unwieldy.

sbt build files are defined using a Scala-based DSL. Simple builds use only a small subset of the DSL, so there's no need to spend any time learning Scala. If you use an sbt Giter8 to get started, you'll have a working build from the start. From there, you'll probably only need to make small edits, or copy-and-paste existing code.

### Setting the Scala version

Even though you'll write your services in Java, Lagom itself uses Scala, so every Lagom build must specify a Scala version, like this:

@[scala-version](code/lagom-build.sbt)

### Defining a service

Next we need to define the projects.  Recall that each service has at least two projects, API and implementation. First we'll define the `hello-api` project.

A Lagom API project is an ordinary sbt project. Our first project looks like this:

@[hello-api](code/lagom-build.sbt)

The first line defines the project itself, by declaring a `lazy val` of type `Project`. (sbt tip: declaring projects using `lazy val` instead of just `val` can prevent some issues with initialization order.)

The project is defined to be the `hello-api` directory, as indicated by `project in file("hello-api")`.  This means all the source code for this project will be under that directory, laid out according to the usual Maven structure (which sbt adopts as well).  So our main Java sources go in `hello-api/src/main/java`.

More settings follow, in which we set the project version and add a library dependency.  The Lagom plugin provides some predefined values to make the Lagom libraries easy to add. In this case, we're using `lagomJavadslApi`. (You can add other dependencies using the usual sbt shorthand for specifying the library's `groupId`, `artifactId` and `version`; see [Library dependencies](https://www.scala-sbt.org/1.x/docs/Library-Dependencies.html) in the sbt documentation.)

Now we need to define the implementation project:

@[hello-impl](code/lagom-build.sbt)

The API project didn't need any plugins enabled, but the implementation project does. Enabling the `LagomJava` plugin adds necessary settings and dependencies and allows us to run the project in development.

The implementation project declares a dependency on the `hello-api` project, so it can implement the API's interfaces.

### Selecting an HTTP backend


Play 2.6 introduces a new HTTP backend implemented using Akka HTTP instead of Netty. This switch on the HTTP backend is part of an ongoing effort to replace all building blocks in Lagom for an Akka-based equivalent. Note that when consuming HTTP services, Lagom's Client Factory still relies on a Netty-based Play-WS instance.

#### Backend selection for sbt users

When using `sbt` as a build tool Lagom defaults to using the Akka HTTP backend to serve HTTP.

You can opt-out of Akka HTTP to use a Netty-based HTTP backend: in `sbt` you have to explicitly disable the `LagomAkkaHttpServer` plugin and enable the `LagomNettyServer` plugin. Note that the `LagomAkkaHttpServer` plugin is added by default on any `LagomJava` or `LagomScala` project.

@[hello-stream-netty](code/lagom-build.sbt)


### Adding a second service

Our sample build will include two services, a producer (`hello`) and a consumer (`hello-stream`).

Here's the definition of the second service:

@[hello-stream](code/lagom-build.sbt)

This is mostly similar to the first service.  The main difference is the added dependency on the first service's API, so the second service can call it.

In the next section, we'll see an alternative build structure where each service has its own build.
