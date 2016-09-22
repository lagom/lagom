# How to run a Lagom service standalone

We know that Lagom doesn't prescribe any particular environment, althougth Lagom provide out of the box ConductR, an integrated environment that provide several services to deploy and manage your services, it would be interesting, in some cases, to be able to deploy a service in standalone fashion.

We know that one microservice cannot live without service locator. In development environment, Lagom's plugin provided one service locator out of the box. It exclusively dedicated to the *dev mode*.  The unique thing required to run a Lagom microservice in standalone mode is a service Locator.

Here we will see how run a Lagom service in standalone mode.

*To follow this guide, this imply you have one Zookeeper instance accessible.*

As service locator registry, we will used Zookeeper. For that, we will use the [James Boner's project](https://github.com/jboner/lagom-service-locator-zookeeper) that implements service Locator interface of Lagom for Zookeeper.

This guide will explain how to :

* integrate the zookeeper service locator in your Lagom's project
* package a service to run it in standalone manner

We will use the following service implementation :


@[content](code/docs/production/HelloWorldService.java)

@[content](code/docs/production/HelloWorldServiceImpl.java)


## How integrate the zookeeper service locator in your Lagom's project

First step will be to add an implementation of the zookeeper service locator into your Lagom service.
For that, we have to build locally the lagom-zookeeper-service-locator project.
We have to do that because the project is under snapshot version.
Well, clone the project with the command below :

```console
git clone https://github.com/jboner/lagom-service-locator-zookeeper.git
```
 then publish locally the project as below :

```console
> sbt publishLocal
```

Once is published, we have to add the project as dependencies in your service, open your *build.sbt* file et add line as below :

```console
libraryDependencies in ThisBuild +=  "com.lightbend.lagom" % "lagom-service-locator-zookeeper_2.11" % "1.0.0-SNAPSHOT"
```
Now the service locator must be activate. We have to declare the module's class in the service configuration file (by default, application.conf).

//declaration line of module

Here, your service is able to work with the service locator but it is not yet localizable. We going to modify the service module class to ensure that the service becomes localizable.

//Code de la classe module 

You noticed that the code will be enabled only in production environment to avoid any conflict with service locator from dev environment.
Now your service is ready to be used in standalone manner. Next, we will see how packaged and run the service.


## How package a service to run in standalone manner

This step is very simple. To package your service, from sbt console you should select service implementation project by the command :

```console
> project helloWorld-imp
```

Once, it is selected, simply run the following command :

```console
> dist
```

Then to start your service in standalone manner, go into the following path:

```console
/helloworld/helloWorld-impl/target/universal/
```
Here, you will find an archive named by the service implementation name, in our case **helloworldimpl-[current_version].zip**
In our case, name should be

```console
helloworldimpl-1.0-SNAPSHOT.zip
```
This archive contains all elements require to run in standalone manner.

Unzip archive :

```console
unzip helloworldimpl-1.0-SNAPSHOT.zip
```

and finally, run the script localized in bin directory :

```console
./helloworldimpl-1.0-SNAPSHOT/bin/helloworldimpl
```

Now, your Lagom service started in standalone manner. It can be interact with all your others services.

This confirguration should be add in all yours services that compose your microservices system. 
