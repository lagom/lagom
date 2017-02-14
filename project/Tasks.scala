/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
import sbt._

object Generators {
  // Generates a scala file that contains the Lagom version for use at runtime.
  def version(lagomVersion: String, dir: File): Seq[File] = {
    val file = dir / "com"/ "lightbend" / "lagom" / "core" / "LagomVersion.scala"
    val scalaSource =
        """|package com.lightbend.lagom.core
           |
           |object LagomVersion {
           |    val current = "%s"
           |}
          """.stripMargin.format(lagomVersion)

    if (!file.exists() || IO.read(file) != scalaSource) {
      IO.write(file, scalaSource)
    }

    Seq(file)
  }
}
