/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.sbt

import sbt._

object InternalConfigs {
  private[sbt] val devModeConfig = config("lagom-dev").hide
  private[sbt] val cassandraDevModeConfig = config("lagom-dev-cassandra").hide
}
