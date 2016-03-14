# Lightbend ConductR

ConductR is a container orchestration tool with the main goal of delivering operational productivity. ConductR is also designed to  host your Lagom services with resilience.

ConductR is free for development usage and there is a "sandbox" so that you can run ConductR locally and test your services. Please visit [ConductR's product page](http://lightbend.com/products/conductr) in order to download the sandbox, and also for more information on ConductR in general. If you'd like to know more about our commercial license then [please contact us](https://www.lightbend.com/company/contact). The remainder of this guide will discuss the specific integration points between Lagom and ConductR.

## Packaging your services

We have integrated the experience of packaging your Lagom services so that you can deliver them to ConductR with ease. By adding the [sbt-lagom-bundle plugin](https://github.com/typesafehub/sbt-lagom-bundle#lagom-bundle-plugin) you are able to package Lagom services for ConductR. In your project's `plugins.sbt`:

```scala
addSbtPlugin("com.typesafe.sbt" % "sbt-lagom-bundle" % "1.0.1")
```

You can then package your services from within the activator console (we `reload` so that activator recognises the new plugin):

```console
> reload
> bundle:dist
...
[info] Bundle has been created: .../my-service/myservice-impl/target/bundle/myservice-impl-v1-06f3e5872f48d69ee339b0a4b7ae382871b69de1cfc1ab831b0a18064d096733.zip
[success] Total time: 4 s, completed 05/03/2016 2:43:07 PM
```

The above creates what is known as a "bundle". A bundle is the unit of deployment with ConductR, and its filename is fingerprinted with a hash value representing the contents of the entire zip file. ConductR does this so that you can be assured that this particular bundle will always be what you knew it to be when it was generated. If its contents change then its filename will also change. ConductR also verifies that the hash provided matches the actual contents at the point that you load it. When managing your Lagom services you can therefore roll releases forward and backward with surety.

## Loading and running your services during development

From a development perspective ConductR provides a "sandbox" environment where you can start it and then test your bundle long before it goes to production. ConductR's sandbox also permits you to [debug Lagom services](https://github.com/typesafehub/sbt-conductr-sandbox#debugging-application-in-conductr-sandbox) from your IDE when running within its cluster.

To access ConductR's sandbox from within the activator console first added the following sbt plugin to your `project/plugins.sbt`:

```scala
addSbtPlugin("com.typesafe.conductr" % "sbt-conductr-sandbox" % "1.4.1")
```

Additionally it is necessary to install the [conductr-cli](https://github.com/typesafehub/conductr-cli) which is used by the sandbox to communicate with the ConductR cluster. To start the sandbox from within the console you  (supposing the version of ConductR is `1.1.2` - you should [check to ensure that you are using a recent version](https://www.lightbend.com/product/conductr/developer)):

```console
> reload
> set SandboxKeys.imageVersion in Global := "YOUR_CONDUCTR_SANDBOX_VERSION"
> sandbox run
```

With the sandbox running you can now load the bundle that you previously generated. Supposing that you're wanting to load the "Helloworld" sample:

```console
> helloworldImpl/conduct load <press the tab key here>
```

Note how you're loading the implementation of your service.

Finally, to run it:

```console
> conduct run helloworldimpl
```

You should now have a running Lagom service. You can also `conduct stop` and `conduct unload` Lagom services with the sandbox. The `conduct` command therefore allows you to manage the full lifecycle of a bundle. In addition you can use `conduct logs` to view the consolidated logging of bundles throughout the cluster - this is particularly useful during development.

## Loading and running your services outside of development

The sandbox is useful to validate that the packaging of your service is correct. However at some point you will want to load and run your bundle on a real ConductR cluster. While it is beyond the scope of this document to describe how to set up such a cluster (please refer to the [ConductR installation guide](https://conductr.lightbend.com/docs/1.1.x/Install) for that), you generally interact with a real cluster through [the ConductR CLI](https://github.com/typesafehub/conductr-cli#command-line-interface-cli-for-typesafe-conductr). You will have already downloaded the CLI as part of the sandbox. The CLI commands are very similar to their activator console counterparts. Type `conduct --help` outside of the sbt console for more information on what commands are available.

## Running Cassandra

If your Lagom service uses Cassandra for persistence then you can generate what is known as a "bundle configuration" for Cassandra. To do this, from a terminal window:

```console
> activator cassandra-configuration:dist
...
[info] Bundle has been created: .../chirper/target/configuration-bundle/cassandra-configuration-06f3e5872f48d69ee339b0a4b7ae382871b69de1cfc1ab831b0a18064d096733.zip
[success] Total time: 4 s, completed 05/03/2016 2:43:07 PM
```

You can then load both Cassandra and the configuration for your project into ConductR:

```console
> conduct load cassandra .../chirper/target/configuration-bundle/cassandra-configuration-06f3e5872f48d69ee339b0a4b7ae382871b69de1cfc1ab831b0a18064d096733.zip
```

Upon loading you then run cassandra at the scale that you require. For convenience we recommend that you start with one Cassandra cluster per root sbt project, which of course can contain many Lagom projects (and therefore services). Bounded contexts are always maintained via separate key-spaces, and so having one Cassandra cluster is viable for supporting many microservices. The actual number of Cassandra clusters required will be the _Lagom amount_ i.e. "just the right amount" for your system. For more information on configuring Cassandra for ConductR please visit [the bundle's website](https://github.com/typesafehub/conductr-cassandra#conductr-cassandra).

## For more information

For more information on ConductR please visit its [documentation site](https://conductr.lightbend.com/).
