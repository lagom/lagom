scalaVersion := "2.12.8"

val commonSettings = Seq(
  organization := "com.lightbend.lagom",

  scalacOptions ++= List(
    "-unchecked",
    "-deprecation",
    "-language:_",
    "-encoding", "UTF-8"
  ),
  javacOptions ++= List(
    "-Xlint:unchecked",
    "-Xlint:deprecation"
  )
)

lazy val root = (project in file("."))
  .enablePlugins(NoPublish)
  .settings(
    name := "lagom-akka-discovery-root"
  )
  .aggregate(serviceLocatorCore, serviceLocatorJavadsl, serviceLocatorScaladsl)

lazy val serviceLocatorCore = (project in file("service-locator/core"))
  .settings(commonSettings)
  .settings(
    name := "lagom-akka-service-locator-core",
    libraryDependencies ++= Dependencies.serviceLocatorCore
  )

lazy val serviceLocatorJavadsl = (project in file("service-locator/javadsl"))
  .settings(commonSettings)
  .settings(
    name := "lagom-javadsl-akka-service-locator",
    libraryDependencies ++= Dependencies.serviceLocatorJavadsl
  ).dependsOn(serviceLocatorCore)

lazy val serviceLocatorScaladsl = (project in file("service-locator/scaladsl"))
  .settings(commonSettings)
  .settings(
    name := "lagom-scaladsl-akka-service-locator",
    libraryDependencies ++= Dependencies.serviceLocatorScaladsl
  ).dependsOn(serviceLocatorCore)

