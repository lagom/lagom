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

This system has two services, one called `hello`, and one called `hello-stream`.  Each service has two sbt projects defined, an API project, `hello-api` and `hello-stream-api`, and an implementation project, `hello-impl` and `hello-stream-impl`.  Additionally, `hello-stream-impl` depends on `hello-api`, and uses that to invoke calls on `hello-stream`.

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

It's important that you set the version of Scala you're using, like so:

@[scala-version](code/lagom-build.sbt)

### Defining a service

Next we need to define the projects.  Recall that each service has at least two projects, API and implementation. First we'll define the `hello-api` project.

A Lagom API project is an ordinary sbt project. Our first project looks like this:

@[hello-api](code/lagom-build.sbt)

The first line defines the project itself, by declaring a `lazy val` of type `Project`. (sbt tip: declaring projects using `lazy val` instead of just `val` can prevent some issues with initialization order.)

The project is defined to be the `hello-api` directory, as indicated by `project in file("hello-api")`.  This means all the source code for this project will be under that directory, laid out according to the usual Maven structure (which sbt adopts as well).  So our main Scala sources go in `hello-api/src/main/scala`.

More settings follow, in which we set the project version and add a library dependency.  The Lagom plugin provides some predefined values to make the Lagom libraries easy to add. In this case, we're using `lagomScaladslApi`. (You can add other dependencies using the usual sbt shorthand for specifying the library's `groupId`, `artifactId` and `version`; see [Library dependencies](https://www.scala-sbt.org/1.x/docs/Library-Dependencies.html) in the sbt documentation.)

Now we need to define the implementation project:

@[hello-impl](code/lagom-build.sbt)

The API project didn't need any plugins enabled, but the implementation project does. Enabling the `LagomScala` plugin adds necessary settings and dependencies and allows us to run the project in development.

The implementation project declares a dependency on the `hello-api` project, so it can implement the API's interfaces.

### Adding a second service

Our sample build will include two services, a producer (`hello`) and a consumer (`hello-stream`).

Here's the definition of the second service:

@[hello-stream](code/lagom-build.sbt)

This is mostly similar to the first service.  The main difference is the added dependency on the first service's API, so the second service can call it.

In the next section, we'll see an alternative build structure where each service has its own build.
