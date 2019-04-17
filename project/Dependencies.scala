/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

import sbt._

object Dependencies {

  object Versions {
    val Lagom = "1.5.0"
    val Akka = "2.5.22"
    val ScalaTest = "3.0.7"
  }

  val akkaDiscovery = "com.typesafe.akka" %% "akka-discovery" % Versions.Akka

  val slf4j = "org.slf4j" % "slf4j-api" % "1.7.26" % Provided
  val lagomJavadslClient = "com.lightbend.lagom" %% "lagom-javadsl-client" % Versions.Lagom % Provided
  val lagomScaladslClient = "com.lightbend.lagom" %% "lagom-scaladsl-client" % Versions.Lagom % Provided

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

}
