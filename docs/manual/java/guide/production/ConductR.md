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

How you build and deploy your app to ConductR depends on which build tool you are using.

* [[Using ConductR with Maven|ConductRMaven]]
* [[Using ConductR with sbt|ConductRSbt]]
