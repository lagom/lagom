/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.sbt

import sbt._
import sbt.internal.inc.Analysis

object LagomReloadableServiceCompat {
  trait autoImport {
    val lagomReload = taskKey[Analysis](
      "Task executed to recompile (and possibly reload) the app when there are changes in sources"
    )
  }

  def joinAnalysis(analysisSeq: Seq[xsbti.compile.CompileAnalysis]) =
    analysisSeq.map(_.asInstanceOf[Analysis]).reduceLeft(_ ++ _)
}
