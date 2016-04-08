# Production

Lagom leverages the [sbt-native-packager](http://www.scala-sbt.org/sbt-native-packager/) to produce archives of various types. By default zip archives can be produced, but you can also produce tar.gz, MSI, debian, RPM, Docker and more.

Running a package requires the provision of a service locator implementation i.e. something that provides the ability for your service to be able to lookup the location of another dynamically at runtime. At a technical level, you provide an implementation of a [ServiceLocator](api/java/index.html?com/lightbend/lagom/javadsl/api/ServiceLocator.html).

When considering a platform to manage your microservices we recommend that you consider the following:

* a means to manage configuration distinctly from your packaged artifact;
* consolidated logging across many nodes;
* a supervisory system whereby if your service(s) terminate unexpectedly then they are automatically restarted;
* the ability to scale up and down with ease and with speed;
* handling of network failures, in particular those that can lead to a split brain scenario;
* automated seed node discovery when requiring more than one instance of your service so that they may share a cluster;
* the ability to perform rolling updates of your services;
* support for your services being monitored across a cluster; and
* the ability to test your services locally prior to them being deployed.

To this end, we have integrated our ConductR product which takes care of all of the above. However Lagom itself has no dependencies on ConductR and you are free to use something else.

ConductR is discussed next.
