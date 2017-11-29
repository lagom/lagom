/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.sbt

import sbt._
import sbt.internal.inc.Analysis

import scala.concurrent.duration.Duration

object LagomReloadableServiceCompat {
  trait autoImport {
    val lagomReload = taskKey[sbt.internal.inc.Analysis]("Executed when sources of changed, to recompile (and possibly reload) the app")
  }

  def joinAnalysis(analysisSeq: Seq[xsbti.compile.CompileAnalysis]) = analysisSeq.map(_.asInstanceOf[Analysis]).reduceLeft(_ ++ _)
}

trait LagomPluginCompat {
  def getPollInterval(pollInterval: Duration) = pollInterval.toMillis.toInt
}
