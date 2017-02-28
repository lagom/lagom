# Lightbend ConductR

ConductR is a container orchestration tool with the main goal of delivering operational productivity. ConductR is also designed to host your Lagom services with resilience.

ConductR is free for development usage and comes with a "sandbox" so that you can run ConductR locally and test your services. For more information on ConductR go to:

* [ConductR product page](http://lightbend.com/products/conductr)
* [ConductR documentation](http://conductr.lightbend.com)

This guide will explain how to:

* Install ConductR sandbox
* Start local ConductR cluster
* Conveniently package, load and run your entire Lagom system
* Package Lagom services individually
* Load and run services during and outside of development

If you'd like to know more about our commercial license then [please contact us](https://www.lightbend.com/company/contact).

## Installing ConductR sandbox

The ConductR sandbox is a utility to easily create a ConductR cluster locally. To run ConductR on your development machine follow the instructions on the [ConductR developer page](https://www.lightbend.com/product/conductr/developer) up to the section **Configure address aliases**. We will run the sandbox later on in this guide. Note that in order to access this page you need to login with your Lightbend account. If you don't have an account yet, head out to the [Sign Up page](https://www.lightbend.com/account/register) and create a free account.

Verify the successful installation of the `conductr-cli` by running the `conduct` command within the terminal:

```console
$ conduct
usage: conduct [-h]
               {version,info,services,acls,load,run,stop,unload,events,logs}
               ...
```

You can also verify the successful installation of the ConductR sandbox by running `sandbox` within the terminal:

```console
$ sandbox
usage: sandbox [-h] {run,debug,stop,init} ...
```

## Using ConductR with your build

To use ConductR within sbt, add the [sbt-conductr plugin](https://github.com/typesafehub/sbt-conductr) to your `project/plugins.sbt`:

@[sbt-conductr](code/conductr.sbt)

sbt-conductr adds several commands to the sbt console:

* `install` to introspect your project and deploy all services within the ConductR sandbox
* `generateInstallationScript` to produce a deployment script for all your services that you can then tailor
* `bundle:dist` to produce individual ConductR packages for your services
* `configuration:dist` to produce individual ConductR configurations for your services
* Commands from the `conductr-cli`

It also adds the ConductR libraries to your classpath, which provide components, including the service locator and other components necessary for initialization on ConductR, which you can mix in with your application cake. To do so, mix `ConductRApplicationComponents` into your production cake:

@[conductr-application](code/ConductR.scala)

Once you have added this to each of your services, you should be ready to run in ConductR. Also note that it's very important to implement the `describeServices` method on `LagomApplicationLoader`, as this will ensure that the ConductR sbt tooling is able to correctly discover the Lagom service APIs offered by each service.

## Run it all

The simplest thing that you can do in order to deploy your entire Lagom system is to run a local ConductR cluster and then run the `install` command.

To start a ConductR cluster locally you should use the ConductR sandbox. With this utility you can easily spin up multiple ConductR nodes on your local machine. The `sandbox run` command will download ConductR and start several ConductR nodes locally. In order to use this command we need to specify the ConductR version. Note that this version is the version of ConductR itself and not the version of the `sbt-conductr` plugin. Please visit again the [ConductR Developer page](https://www.lightbend.com/product/conductr/developer) to pick up the latest ConductR version from the section **Run the Sandbox**.

First, start the sbt console from the terminal:

```console
$ sbt
```

Now start the local ConductR cluster with the `sandbox run` command:

```console
> sandbox run <CONDUCTR_VERSION>
```

You can then install your entire Lagom system in one go:

```console
> install
```

The install command will introspect your project and its sub-projects and then package, load and run everything in ConductR at once - including Cassandra. The local sandbox is expected to be running and it will first be restarted to ensure that it is in a clean state.

We expect that you will use the `install` command early on, but graduate to ConductR's lower-level commands as you evolve your services through their development lifecycle.

The remainder of this document describes the lower level ConductR commands.

## Packaging your services

Packaging your Lagom services so that you can deliver them to ConductR is straightforward. To do this then use the `bundle:dist` command from within the sbt console:

```console
> bundle:dist
...
[info] Bundle has been created: /my-service/myservice-impl/target/bundle/myservice-impl-v1-06f3e5872f48d69ee339b0a4b7ae382871b69de1cfc1ab831b0a18064d096733.zip
[success] Total time: 4 s, completed 05/03/2016 2:43:07 PM
```

The above creates what is known as a "bundle". A bundle is the unit of deployment with ConductR, and its filename is fingerprinted with a hash value representing the contents of the entire zip file. ConductR does this so that you can be assured that this particular bundle will always be what you knew it to be when it was generated. If its contents change then its filename will also change. ConductR also verifies that the hash provided matches the actual contents at the point that you load it. When managing your Lagom services you can therefore roll releases forward and backward with surety.

## Loading and running your services during development

With the ConductR sandbox running you can load the bundle that you previously generated:

```console
> project my-service-impl
> conduct load <press the tab key here>
```

When starting the sbt console you are in the context of the root project. However, the bundles are created for your sub projects, i.e. your service implementations. Therefore, it is necessary to first switch with `project my-service-impl` to the sub project. Replace `my-service-impl` with the name of your sub project.

Finally, to run the bundle on ConductR use:

```console
> conduct run my-service-impl
Bundle run request sent.
Bundle 9849508f27cdd39742f8e455795538b6 waiting to reach expected scale 1
Bundle 9849508f27cdd39742f8e455795538b6 has scale 0, expected 1
Bundle 9849508f27cdd39742f8e455795538b6 expected scale 1 is met
Stop bundle with: conduct stop --ip 192.168.99.100 9849508
Print ConductR info with: conduct info --ip 192.168.99.100
[success] Total time: 4 s, completed 05/03/2016 2:43:07 PM
```

Now, the Lagom service should run in your local ConductR cluster. The IP address of your cluster is the Docker host IP address. To pick up the IP address check out the previous console output of the `conduct run` command. The default port of a Lagom service on ConductR is `9000`, e.g. considering the ConductR IP address is `192.168.99.100` then the running service is available at `http://192.168.99.100:9000/my/service/path`.

You can also check the state of your cluster with:

```console
> conduct info
```

The `conduct` command allows you to manage the full lifecycle of a bundle. You can also use `conduct stop my-service-impl` and `conduct unload my-service-impl` to stop and unload your Lagom services. In addition you can use `conduct logs my-service-impl` to view the consolidated logging of bundles throughout the cluster. This is particularly useful during development.

To stop the ConductR sandbox use:

```console
> sandbox stop
```

## Loading and running your services outside of development

The sandbox is useful to validate that the packaging of your service is correct. However, at some point you want to load and run your bundle on a real ConductR cluster. While it is beyond the scope of this document to describe how to set up such a cluster (please refer to the [ConductR installation guide](https://conductr.lightbend.com/docs/2.0.x/Install) for that), you generally interact with a real cluster through [the ConductR CLI](https://github.com/typesafehub/conductr-cli#command-line-interface-cli-for-typesafe-conductr). You have already downloaded the CLI as part of the sandbox. The CLI commands are identical to their sbt console counterparts. Type `conduct --help` for more information on what commands are available.

## Running Cassandra

If your Lagom service uses Cassandra for persistence then you use a pre-configured bundle to run Cassandra inside of ConductR.

First, load the Cassandra on to ConductR:

```console
> conduct load cassandra
```

To run the cassandra bundle execute:

```
> conduct run cassandra
```

If the Cassandra bundle has been started on ConductR after the Lagom service itself then it will take a couple of seconds until the Lagom service connects to Cassandra.

For convenience we recommend that you start with one Cassandra cluster per root sbt project, which of course can contain many Lagom projects (and therefore services). Bounded contexts are always maintained via separate key-spaces, and so having one Cassandra cluster is viable for supporting many microservices. The actual number of Cassandra clusters required will be the _Lagom amount_ i.e. "just the right amount" for your system. For more information on configuring Cassandra for ConductR please visit [the bundle's website](https://github.com/typesafehub/conductr-cassandra).
