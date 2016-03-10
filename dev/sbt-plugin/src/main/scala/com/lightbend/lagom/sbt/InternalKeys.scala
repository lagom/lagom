/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.sbt

import sbt.SettingKey
import play.sbt.PlayInteractionMode
import sbt.TaskKey

object InternalKeys {
  val interactionMode = SettingKey[PlayInteractionMode]("interactionMode", "Hook to configure how a service blocks when running")
  val stop = TaskKey[Unit]("stop", "Stop services, if have been started in non blocking mode")
}
