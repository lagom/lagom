/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.sbt

import com.lightbend.lagom.core.LagomVersion

import sbt.Keys._
import sbt._

object LagomImport {
  private val moduleOrganization = "com.lightbend.lagom"
  def component(id: String) = moduleOrganization %% id % LagomVersion.current

  private[sbt] val lagomLogbackModuleName = "lagom-logback"

  val lagomJavadslApi = component("lagom-javadsl-api")
  val lagomJavadslClient = component("lagom-javadsl-client")
  val lagomJavadslCluster = component("lagom-javadsl-cluster")
  // Scoped to `Provided` because it's needed only at compile-time. 
  val lagomJavadslImmutables = component("lagom-javadsl-immutables") % Provided
  val lagomJavadslJackson = component("lagom-javadsl-jackson")
  val lagomJavadslBroker = component("lagom-javadsl-broker")
  val lagomJavadslKafkaClient = component("lagom-javadsl-kafka-client")
  val lagomJavadslKafkaBroker = component("lagom-javadsl-kafka-broker")
  val lagomJavadslPersistence = component("lagom-javadsl-persistence")
  val lagomJavadslPersistenceCassandra = component("lagom-javadsl-persistence-cassandra")
  val lagomJavadslPersistenceJdbc = component("lagom-javadsl-persistence-jdbc")
  val lagomJavadslPersistenceJpa = component("lagom-javadsl-persistence-jpa")
  val lagomJavadslPubSub = component("lagom-javadsl-pubsub")
  val lagomJavadslServer = component("lagom-javadsl-server")
  val lagomJavadslTestKit = component("lagom-javadsl-testkit") % Test
  val lagomLogback = component(lagomLogbackModuleName)
  val lagomLog4j2 = component("lagom-log4j2")

  val lagomScaladslApi = component("lagom-scaladsl-api")
  val lagomScaladslClient = component("lagom-scaladsl-client")
  val lagomScaladslServer = component("lagom-scaladsl-server")
  val lagomScaladslCluster = component("lagom-scaladsl-cluster")
  val lagomScaladslBroker = component("lagom-scaladsl-broker")
  val lagomScaladslKafkaClient = component("lagom-scaladsl-kafka-client")
  val lagomScaladslKafkaBroker = component("lagom-scaladsl-kafka-broker")
  val lagomScaladslPersistence = component("lagom-scaladsl-persistence")
  val lagomScaladslPersistenceCassandra = component("lagom-scaladsl-persistence-cassandra")
  val lagomScaladslPersistenceJdbc = component("lagom-scaladsl-persistence-jdbc")
  val lagomScaladslPubSub = component("lagom-scaladsl-pubsub")
  val lagomScaladslTestKit = component("lagom-scaladsl-testkit") % Test

  val lagomJUnitDeps = Seq(
    "junit" % "junit" % "4.12" % Test,
    "com.novocode" % "junit-interface" % "0.11" % Test
  )

  // for forked tests, necessary for Cassandra
  def lagomForkedTestSettings: Seq[Setting[_]] = Seq(
    fork in Test := true,
    concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
    javaOptions in Test ++= Seq("-Xms256M", "-Xmx512M"),
    testGrouping in Test <<= definedTests in Test map singleTestsGrouping
  )

  // group tests, a single test per group
  private def singleTestsGrouping(tests: Seq[TestDefinition]) = {
    // We could group non Cassandra tests into another group
    // to avoid new JVM for each test, see http://www.scala-sbt.org/release/docs/Testing.html
    val javaOptions = Seq("-Xms256M", "-Xmx512M")
    tests map { test =>
      new Tests.Group(
        name = test.name,
        tests = Seq(test),
        runPolicy = Tests.SubProcess(javaOptions)
      )
    }
  }
}
