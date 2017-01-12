/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.sbt

import sbt._
import sbt.Keys._

import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._

object LagomSettings {
  lazy val defaultSettings = Seq[Setting[_]](
    javacOptions in Compile := Seq("-g", "-encoding", "UTF-8", "-source", "1.8", "-target", "1.8", "-parameters", "-Xlint:unchecked", "-Xlint:deprecation"),
    javacOptions in (Compile, doc) := Seq("-noqualifier", "java.lang", "-encoding", "UTF-8", "-source", "1.8"),

    // Native packaging
    mainClass in Compile := Some("play.core.server.ProdServerStart"),

    mappings in Universal ++= {
      val docDirectory = (doc in Compile).value
      val docDirectoryLen = docDirectory.getCanonicalPath.length
      val pathFinder = docDirectory ** "*"
      pathFinder.get map {
        docFile: File =>
          docFile -> ("share/doc/api/" + docFile.getCanonicalPath.substring(docDirectoryLen))
      }
    },

    mappings in Universal ++= {
      val pathFinder = baseDirectory.value * "README*"
      pathFinder.get map {
        readmeFile: File =>
          readmeFile -> readmeFile.getName
      }
    },

    // Native packager
    sourceDirectory in Universal := baseDirectory.value / "dist"
  )
}
