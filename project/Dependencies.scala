import sbt._
import sbt.Keys._

object Dependencies {

  object Versions {
    val Lagom14 = "1.4.9"
    val AkkaManagement = "0.19.0"
    val ScalaTest = "3.0.5"
    val Play26 = "2.6.20"
  }

  val akkaDiscovery = "com.lightbend.akka.discovery" %% "akka-discovery" % Versions.AkkaManagement
  val akkaClusterHttp = "com.lightbend.akka.management" %% "akka-management-cluster-http" % Versions.AkkaManagement
  val akkaClusterBootstrap = "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % Versions.AkkaManagement
  val akkaManagement = "com.lightbend.akka.management" %% "akka-management" % Versions.AkkaManagement

  val slf4j = "org.slf4j" % "slf4j-api" % "1.7.25" % Provided
  val lagomJavadslClient = "com.lightbend.lagom" %% "lagom-javadsl-client" % Versions.Lagom14 % Provided
  val lagomScaladslClient = "com.lightbend.lagom" %% "lagom-scaladsl-client" % Versions.Lagom14 % Provided

  val play = "com.typesafe.play" %% "play" % Versions.Play26 % Provided

  val scalaTest = "org.scalatest" %% "scalatest" % Versions.ScalaTest % Test
  val logback = "ch.qos.logback" % "logback-classic" % "1.2.3" % Test

  val serviceLocatorCore = Seq(
    akkaDiscovery,
    slf4j,
    scalaTest,
    logback
  )

  val serviceLocatorJavadsl = Seq(
    lagomJavadslClient
  )

  val serviceLocatorScaladsl = Seq(
    lagomScaladslClient
  )

  val bootstrap = Seq(
    akkaManagement,
    akkaClusterHttp,
    akkaClusterBootstrap,
    play
  )

}
