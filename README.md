# Akka Discovery based ServiceLocator

This project provides:

* [`AkkaDiscoveryServiceLocator`][], a Lagom `ServiceLocator` implementation based on [Akka Discovery][]
* [`AkkaDiscoveryServiceLocatorModule`][], a Guice `Module` to wire-up the `AkkaDiscoveryServiceLocator` when using runtime-time DI.
* [`AkkaDiscoveryComponents`][], a cake component to wire-up the `AkkaDiscoveryServiceLocator` when using compile-time DI (macwire).

Additionally, you will need to choose one of the existing [Akka Discovery][] implementations and configure it for production in your application.

[`AkkaDiscoveryServiceLocatorModule`]: service-locator/javadsl/src/main/java/com/lightbend/lagom/javadsl/akka/discovery/AkkaDiscoveryServiceLocatorModule.java
[`AkkaDiscoveryServiceLocator`]: service-locator/javadsl/src/main/java/com/lightbend/lagom/javadsl/akka/discovery/AkkaDiscoveryServiceLocator.java
[`AkkaDiscoveryComponents`]: service-locator/scaladsl/src/main/scala/com/lightbend/lagom/scaladsl/akka/discovery/AkkaDiscoveryComponents.scala
[Akka Discovery]: https://doc.akka.io/docs/akka/2.5/discovery/index.html

## Configuration for Lagom Java

For `sbt` users, add the dependencies to your `build.sbt` file:

```sbt
lazy val `hello-impl` = (project in file("hello-impl"))
  .enablePlugins(LagomJava)
  .settings(
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-javadsl-akka-discovery-service-locator" % "0.1.0",

      // for example, include Akka Discovery Kubernetes implementation
      // see https://doc.akka.io/docs/akka/2.5/discovery/index.html
      "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % "1.0.0"
    )
  )
```

For `maven` users, add the dependencies to your pom.xml

```xml
<dependency>
    <groupId>com.lightbend.lagom</groupId>
    <artifactId>lagom-javadsl-akka-discovery-service-locator_2.12</artifactId>
    <version>0.1.0</version>
</dependency>

<!--
  for example, include Akka Discovery Kubernetes implementation
  see https://doc.akka.io/docs/akka/2.5/discovery/index.html
-->
<dependency>
    <groupId>com.lightbend.akka.discovery</groupId>
    <artifactId>akka-discovery-kubernetes-api_2.12</artifactId>
    <version>1.0.0</version>
</dependency>
```


The Guice module [`AkkaDiscoveryServiceLocatorModule`][] will be added by default to your project, but will only wire in the [`AkkaDiscoveryServiceLocator`][] when running in production mode.

In development, your Lagom application will keep using the Lagom's dev-mode `ServiceLocator`.

Next, you will need to configure Akka Discovery in your `application.conf` file. Consult the [Akka Discovery][] documentation for further instructions.

## Configuration for Lagom Scala

Add the dependencies to your `build.sbt` file:

```sbt
lazy val `hello-impl` = (project in file("hello-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-scaladsl-akka-discovery-service-locator" % "0.1.0",

      // for example, include Akka Discovery Kubernetes implementation
      // see https://doc.akka.io/docs/akka/current/discovery/index.html
      "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % "1.0.0"
    )
  )
```

Wire [`AkkaDiscoveryComponents`][] in your `ApplicationLoader`:

```scala
class HelloServiceLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new HelloProxyApplication(context) with AkkaDiscoveryComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new HelloProxyApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[HelloService])

}
```

Next, you will need to configure Akka Discovery in your `application.conf` file. Consult the [Akka Discovery][] documentation for further instructions.
