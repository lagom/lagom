/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.sbt

import sbt.ForkOptions

trait LagomImportCompat {

  def getForkOptions(options: Vector[String]): ForkOptions = ForkOptions().withRunJVMOptions(options)

}
