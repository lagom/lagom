/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.sbt

import sbt._
import sbt.inc.Analysis

object LagomReloadableServiceCompat {
  trait autoImport {
    val lagomReload = taskKey[sbt.inc.Analysis](
      "Task executed to recompile (and possibly reload) the app when there are changes in sources"
    )
  }

  def joinAnalysis(analysisSeq: Seq[Analysis]) = analysisSeq.reduceLeft(_ ++ _)
}

trait LagomPluginCompat {
  def getPollInterval(pollInterval: Int) = pollInterval
}
