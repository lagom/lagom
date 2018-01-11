/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.sbt

import sbt.ForkOptions

trait LagomImportCompat {

  def getForkOptions(options: Seq[String]): ForkOptions = ForkOptions(runJVMOptions = options)

}
