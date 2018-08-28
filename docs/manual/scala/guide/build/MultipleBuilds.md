# Splitting a system into multiple builds

For a small system maintained by a single team, it's fine to have all your services in one build. Doing it that way makes it really easy to run all your services with the `runAll` task, as we'll see later in the [[Running Services|RunningServices]] section of this manual.

If you have multiple teams, though, then as described already in [[Lagom build concepts|BuildConcepts]], we recommend splitting your system into multiple builds.

If you aren't concerned with scaling to multiple teams yet, feel free to skip this section for now.

## Publishing services

Even with multiple builds, you will still often want to run your services together in development.  Lagom allows importing services published from one build into another build.

Suppose you have a `hello` service that you want to publish and import into another build.  You can publish this to your local repository by running `publishLocal` from sbt.  This is the simplest way to publish a service, however it means every developer that wants to run a build that imports the service will need publish it to their own repository themselves, and they'll need to do that for each version that they want to import.

More commonly, many developers can share a single Maven or Ivy repository that they can publish and pull artifacts from.  There are a few options for how to do this, if you're happy to use a hosted repository, [Bintray](https://bintray.com) is a good option, if you want to run the repository locally, [Artifactory](https://www.jfrog.com/open-source/) or [Nexus](https://www.sonatype.com/products-overview) are common solutions.  For information on how to configure these in sbt, see [how to publish artifacts](https://www.scala-sbt.org/1.x/docs/Publishing.html) .

### Publishing to Bintray

Bintray offers both free open source hosting, as well as a paid private hosting service.

If you are using Bintray, the first thing you'll need to do is sign up for an account, and create an organization.  In your Bintray organization, you can then create a Bintray repository, we recommend creating a Maven repository.

Having set Bintray up, you now need to configure your build to publish to this.

First, add the sbt-bintray plugin to your `project/plugins.sbt` file:

@[bintray-plugin](code/multiple-builds.sbt)

The Bintray plugin manages its own credentials, this can be configured by running `sbt bintrayChangeCredentials`, which will save the credentials in `~/.bintray/.credentials`.

Once you've authenticated with Bintray, you can then configure your build to publish to it, by adding the following configuration to `build.sbt`:

@[bintray-publish](code/multiple-builds.sbt)

## Importing a service

The `hello` Lagom service can be imported by adding the following declaration to your build:

@[hello-external](code/multiple-builds.sbt)

The first argument passed to `lagomExternalScaladslProject` is the name that will be used in your build to refer to this externally defined project. While, the second argument provides the dependency to the `hello-impl` JAR, using the conventional sbt syntax for declaring dependencies. Note in fact that the `lagomExternalScaladslProject` method returns a sbt `Project`, which you can further customize if needed.

You can further configure the service (what ports it is available on, the address it is bound to, etc...) using [[the same settings as a managed Lagom Service|ConfiguringServicesInDevelopment]].

## Binding the service

After having added the external Lagom project to your build, we need to provide the binding as it's necessary to consume a service, so that Lagom can provide an implementation for your application to use.  This can be done using `serviceClient.implement[T]` as explained in [[Binding a service client|ServiceClients#Binding-a-service-client]] .

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
