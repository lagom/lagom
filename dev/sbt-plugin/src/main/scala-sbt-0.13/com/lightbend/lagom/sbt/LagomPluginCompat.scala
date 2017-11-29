/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.sbt

import sbt._
import sbt.inc.Analysis

object LagomReloadableServiceCompat {
  trait autoImport {
    val lagomReload = taskKey[sbt.inc.Analysis]("Executed when sources of changed, to recompile (and possibly reload) the app")
  }

  def joinAnalysis(analysisSeq: Seq[Analysis]) = analysisSeq.reduceLeft(_ ++ _)
}

trait LagomPluginCompat {
  def getPollInterval(pollInterval: Int) = pollInterval
}
