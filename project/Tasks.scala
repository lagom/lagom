/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
import sbt._

object Generators {
  // Generates a scala file that contains the Lagom version for use at runtime.
  def version(lagomVersion: String, akkaVersion: String, playVersion: String, dir: File): Seq[File] = {
    val file = dir / "com" / "lightbend" / "lagom" / "core" / "LagomVersion.scala"
    val scalaSource =
      s"""|package com.lightbend.lagom.core
          |
          |object LagomVersion {
          |  val current = "$lagomVersion"
          |  val akka = "$akkaVersion"
          |  val play = "$playVersion"
          |}
           """.stripMargin

    if (!file.exists() || IO.read(file) != scalaSource) {
      IO.write(file, scalaSource)
    }

    Seq(file)
  }
}
