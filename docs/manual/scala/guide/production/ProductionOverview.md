# Production

Lagom doesn't prescribe any particular production environment, however out of the box support is provided for [Lightbend ConductR](https://www.lightbend.com/products/conductr).

## Deploying Lagom services to ConductR

Lightbend ConductR is a perfect match for Lagom, as it provides the following features:

* a means to manage configuration distinctly from your packaged artifact;
* consolidated logging across many nodes;
* a supervisory system whereby if your service(s) terminate unexpectedly then they are automatically restarted;
* the ability to scale up and down with ease and with speed;
* handling of network failures, in particular those that can lead to a split brain scenario;
* automated seed node discovery when requiring more than one instance of your service so that they may share a cluster;
* the ability to perform rolling updates of your services;
* support for your services being monitored across a cluster; and
* the ability to test your services locally prior to them being deployed.

To deploy your Lagom services using ConductR, see [[ConductR]].

## Deploying to other platforms

Lagom sbt support leverages the [sbt-native-packager](http://www.scala-sbt.org/sbt-native-packager/) to produce archives of various types. By default zip archives can be produced, but you can also produce tar.gz, MSI, debian, RPM, Docker and more.

If using Maven, there are many plugins for Maven to produce artifacts for various platforms.

Running a package requires the provision of a service locator implementation i.e. something that provides the ability for your service to be able to lookup the location of another dynamically at runtime. At a technical level, you provide an implementation of a [ServiceLocator](api/com/lightbend/lagom/scaladsl/api/ServiceLocator.html).

### Deploying using static service locations

While we would never advise using static service locations in any production situation, as a means to demonstrating a working Lagom system in the absence of a managed runtime, you may decide to deploy Lagom systems to static locations with a static configuration saying where the systems live.

To aid in achieving this, a [`ConfigurationServiceLocator`](api/com/lightbend/lagom/scaladsl/client/ConfigurationServiceLocator.html) is provided that reads the service locator configuration out of Lagom's `application.conf` file.  Here is an example of the configuration for it:

```
lagom.services {
  serviceA = "http://10.1.2.3:8080"
  serviceB = "http://10.1.2.4:8080"
}
```

To instruct Lagom to use the `ConfigurationServiceLocator`, you can mix in the [`ConfigurationServiceLocatorComponents`](api/com/lightbend/lagom/scaladsl/client/ConfigurationServiceLocatorComponents.html) trait into your application:

@[configuration-service-locator](code/ProductionOverview.scala)

This shows the configuration service locator being used only in prod mode, while the dev mode service locator is used in dev mode by mixing in the `LagomDevModeComponents` trait.