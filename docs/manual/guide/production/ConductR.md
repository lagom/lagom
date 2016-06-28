# Lightbend ConductR

ConductR is a container orchestration tool with the main goal of delivering operational productivity. ConductR is also designed to host your Lagom services with resilience.

ConductR is free for development usage and comes with a "sandbox" so that you can run ConductR locally and test your services. For more information on ConductR go to:

* [ConductR product page](http://lightbend.com/products/conductr)
* [ConductR documentation](http://conductr.lightbend.com)

This guide will explain how to:

* Install ConductR sandbox
* Package Lagom services
* Start local ConductR cluster
* Load and run services during and outside of development
* Run Cassandra on ConductR
 
If you'd like to know more about our commercial license then [please contact us](https://www.lightbend.com/company/contact).

## Installing ConductR sandbox

The ConductR sandbox is a docker image to easily create a ConductR cluster locally. To run ConductR on your development machine follow the instructions on the [ConductR developer page](https://www.lightbend.com/product/conductr/developer) up to the section **Configure Docker VM**. We will run the sandbox later on in this guide. Note that in order to access this page you need to login with your Lightbend account. If you don't have an account yet, head out to the [Sign Up page](https://www.lightbend.com/account/register) and create a free account.
 
Verify the successful installation of the `conductr-cli` by running the `conduct` command within the terminal:

```console
$ conduct
usage: conduct [-h]
               {version,info,services,acls,load,run,stop,unload,events,logs}
               ...
```

Now, add the [sbt-conductr plugin](https://github.com/typesafehub/sbt-conductr) to your `project/plugins.sbt`:

```scala
addSbtPlugin("com.lightbend.conductr" % "sbt-conductr" % "2.1.4")
```

sbt-conductr adds several commands to the activator console:
 
* `bundle:dist` to produce ConductR packages for your services
* `configuration:dist` to produce a custom ConductR configuration for your services
* Commands from the `conductr-cli`

We will use most of these commands in the next sections. 

## Packaging your services

Packaging your Lagom services so that you can deliver them to ConductR is straightforward. Start the activator console from the terminal:

```console
$ activator
```

You can then package your services from within the activator console with the `bundle:dist` command:

```console
> bundle:dist
...
[info] Bundle has been created: /my-service/myservice-impl/target/bundle/myservice-impl-v1-06f3e5872f48d69ee339b0a4b7ae382871b69de1cfc1ab831b0a18064d096733.zip
[success] Total time: 4 s, completed 05/03/2016 2:43:07 PM
```

The above creates what is known as a "bundle". A bundle is the unit of deployment with ConductR, and its filename is fingerprinted with a hash value representing the contents of the entire zip file. ConductR does this so that you can be assured that this particular bundle will always be what you knew it to be when it was generated. If its contents change then its filename will also change. ConductR also verifies that the hash provided matches the actual contents at the point that you load it. When managing your Lagom services you can therefore roll releases forward and backward with surety.

## Loading and running your services during development

To start a ConductR cluster locally you should use the ConductR sandbox which is a docker image based on Ubuntu that includes ConductR. With this docker image you can easily spin up multiple ConductR nodes on your local machine. The `sandbox run` command will pick up and run this ConductR docker image. In order to use this command we need to specify the ConductR version. Note that this version is the version of ConductR itself and not the version of the `sbt-conductr` plugin. Please visit again the [ConductR Developer page](https://www.lightbend.com/product/conductr/developer) to pick up the latest ConductR version from the section **Quick Configuration**.

Now, go to the activator console to start the local ConductR cluster with the `sandbox run` command: 

```console
> sandbox run <CONDUCTR_VERSION>
```

With the ConductR sandbox running you can now load the bundle that you previously generated:

```console
> project my-service-impl
> conduct load <press the tab key here>
```

When starting the activator console you are in the context of the root project. However, the bundles are created for your sub projects, i.e. your service implementations. Therefore, it is necessary to first switch with `project my-service-impl` to the sub project. Replace `my-service-impl` with the name of your sub project.

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

The sandbox is useful to validate that the packaging of your service is correct. However, at some point you want to load and run your bundle on a real ConductR cluster. While it is beyond the scope of this document to describe how to set up such a cluster (please refer to the [ConductR installation guide](https://conductr.lightbend.com/docs/1.1.x/Install) for that), you generally interact with a real cluster through [the ConductR CLI](https://github.com/typesafehub/conductr-cli#command-line-interface-cli-for-typesafe-conductr). You have already downloaded the CLI as part of the sandbox. The CLI commands are identical to their activator console counterparts. Type `conduct --help` for more information on what commands are available.

## Running Cassandra

If your Lagom service uses Cassandra for persistence then you can generate what is known as a "bundle configuration" for Cassandra. First switch to your root project and then generate the bundle configuration:

```console
> project /
> cassandra-configuration:dist
...
[info] Bundle has been created: /my-project/target/configuration-bundle/cassandra-configuration-06f3e5872f48d69ee339b0a4b7ae382871b69de1cfc1ab831b0a18064d096733.zip
[success] Total time: 4 s, completed 05/03/2016 2:43:07 PM
```

You can then load the Cassandra bundle with your Cassandra bundle configuration on to ConductR. Note that in this case `cassandra` represents the bundle and the `cassandra-configuration-<hash>.zip` file is the bundle configuration. The previous command prints out the bundle configuration zip file on the console. Copy this file to load Cassandra with your project specific configuration: 

```console
> conduct load cassandra /my-project/target/configuration-bundle/cassandra-configuration-06f3e5872f48d69ee339b0a4b7ae382871b69de1cfc1ab831b0a18064d096733.zip
```

The tab completion of `conduct load` only works for bundles of the project. The Cassandra bundle is an external bundle hosted on bintray. Therefore tab completion doesn't work in this case.

To run the cassandra bundle execute:

```
> conduct run cassandra
```

If the Cassandra bundle has been started on ConductR after the Lagom service itself then it will take a couple of seconds until the Lagom service connects to Cassandra.

For convenience we recommend that you start with one Cassandra cluster per root sbt project, which of course can contain many Lagom projects (and therefore services). Bounded contexts are always maintained via separate key-spaces, and so having one Cassandra cluster is viable for supporting many microservices. The actual number of Cassandra clusters required will be the _Lagom amount_ i.e. "just the right amount" for your system. For more information on configuring Cassandra for ConductR please visit [the bundle's website](https://github.com/typesafehub/conductr-cassandra#conductr-cassandra).