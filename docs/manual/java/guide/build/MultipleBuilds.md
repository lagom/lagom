# Splitting a system into multiple builds

When designing a Lagom system, you have flexibility to organize your services into build units that best meet your requirements. For a small system maintained by a single team, it's fine to have all your services in one build. Doing it that way makes it really easy to run all your services with the `runAll` task, as we'll see later in the [[Running Services|RunningServices]] section of this manual.

If you have multiple teams, though, then as described already in [[Lagom build concepts|BuildConcepts]], we recommend splitting your system into multiple builds.

If you aren't concerned with scaling to multiple teams yet, feel free to skip this section for now.

## Publishing services

Even with multiple builds, you will still often want to run your services together in development.  Lagom allows importing services published from one build into another build.

Suppose you have a `hello` service that you want to publish and import into another build.  You can publish this to your local repository by running `mvn install` if using Maven, or by running `publishLocal` if using sbt.  This is the simplest way to publish a service, however it means every developer that wants to run a build that imports the service will need publish it to their own repository themselves, and they'll need to do that for each version that they want to import.

More commonly, many developers can share a single Maven or Ivy repository that they can publish and pull artifacts from.  There are a few options for how to do this, if you're happy to use a hosted repository, [Bintray](https://bintray.com) is a good option, if you want to run the repository locally, [Artifactory](https://www.jfrog.com/open-source/) or [Nexus](https://www.sonatype.com/products-overview) are common solutions.  For information on how to configure these in sbt, see [how to publish artifacts](https://www.scala-sbt.org/1.x/docs/Publishing.html).

### Publishing to Bintray

Bintray offers both free open source hosting, as well as a paid private hosting service.

If you are using Bintray, the first thing you'll need to do is sign up for an account, and create an organization.  In your Bintray organization, you can then create a Bintray repository, we recommend creating a Maven repository.

Having set Bintray up, you now need to configure your build to publish to this.

#### Publishing to Bintray using Maven

To publish to Bintray using Maven, you can follow the instructions published by bintray [here](https://blog.bintray.com/2015/09/17/publishing-your-maven-project-to-bintray/).

#### Publishing to Bintray using sbt

First, add the sbt-bintray plugin to your `project/plugins.sbt` file:

@[bintray-plugin](code/multiple-builds.sbt)

The Bintray plugin manages its own credentials, this can be configured by running `sbt bintrayChangeCredentials`, which will save the credentials in `~/.bintray/.credentials`.

Once you've authenticated with Bintray, you can then configure your build to publish to it, by adding the following configuration to `build.sbt`:

@[bintray-publish](code/multiple-builds.sbt)

## Importing a service

### Using Maven

The `lagom-maven-plugin` offers a configuration item called `externalProjects` that can be configured on the root project to import external projects into a Maven build.  For example:

```xml
<plugin>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-maven-plugin</artifactId>
    <version>${lagom.version}</version>
    <configuration>
        <externalProjects>
            <externalProject>
                <artifact>
                    <groupId>com.example</groupId>
                    <artifactId>hello-impl</artifactId>
                    <version>1.2.3</version>
                </artifact>
            </externalProject>
        </externalProjects>
    </configuration>
</plugin>
```

Now when you run `lagom:runAll`, the `hello-impl` service will also be started.  There are a few additional configuration items that `externalProject` supports:

* `playService` - Indicates that this is a Play, rather than a Lagom service. Defaults to `false`.
* `serviceHttpPort` - Allows the http port that the service is run on to be overridden. Defaults to automatic selection of a port by Lagom.
* `serviceHttpsPort` - Allows the https port that the service is run on to be overridden. Defaults to automatic selection of a port by Lagom.
* `serviceAddress` - Allows overriding the host address the service is bound to. Defaults to `127.0.0.1`.
* `cassandraEnabled` - Configures whether this service needs Cassandra or not. Defaults to `true`.

### Using sbt

The `helloworld` Lagom service can be imported by adding the following declaration to your build:

@[hello-external](code/multiple-builds.sbt)

The first argument passed to `lagomExternalJavadslProject` is the name that will be used in your build to refer to this externally defined project. While, the second argument provides the dependency to the `hello-impl` JAR, using the conventional sbt syntax for declaring dependencies. Note in fact that the `lagomExternalJavadslProject` method returns a sbt `Project`, which you can further customize if needed.

You can further configure the service (what ports it is available on, the address it is bound to, etc...) using [[the same settings as a managed Lagom Service|ConfiguringServicesInDevelopment]].

## Using the External Service

After having added the external Lagom project to your build, we need to provide the binding as it's necessary to consume a service, so that Lagom can provide an implementation for your application to use.  This can be done using the `bindClient` method on [ServiceClientGuiceSupport](api/index.html?com/lightbend/lagom/javadsl/client/ServiceClientGuiceSupport.html) as explained in [[Binding a service client|ServiceClients#Binding-a-service-client]] .

@[bind-hello-client](../services/code/docs/services/client/Module.java)

After providing the binding, just type `reload` in the sbt console. Then, when executing `runAll`, you should see that the `hello` service is started, together with all other services defined in the build:


```console
> runAll
[info] ...
[info] Service hello listening for HTTP on 0:0:0:0:0:0:0:0:22407
[info] ...
(Services started, use Ctrl+D to stop and go back to the console...)
```

Now that you have integrated the `hello` service in your build, any of your Lagom projects can communicate with it after adding a library dependency to its `hello-api` artefact:

@[hello-communication](code/multiple-builds.sbt)
