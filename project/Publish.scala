/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package build.play.grpc

import sbt._, Keys._

/**
 * For projects that are not to be published.
 */
object NoPublish extends AutoPlugin {
  override def requires = plugins.JvmPlugin

  override def projectSettings = Seq(
    skip in publish := true,
  )
}

object Publish extends AutoPlugin {
  import bintray.BintrayPlugin
  import bintray.BintrayPlugin.autoImport._

  override def trigger = allRequirements
  override def requires = BintrayPlugin

  override def projectSettings = Seq(
    bintrayOrganization := Some("lagom"),
    bintrayPackage := "akka-discovery-service-locator",
    scmInfo := Some(ScmInfo(url("https://github.com/lagom/akka-discovery-service-locator"), "git@github.com:lagom/akka-discovery-service-locator")),
    licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers += Developer("contributors",
      "Contributors",
      "https://gitter.im/lagom/contributors",
      url("https://github.com/lagom/akka-discovery-service-locator/graphs/contributors")),
  )
}

