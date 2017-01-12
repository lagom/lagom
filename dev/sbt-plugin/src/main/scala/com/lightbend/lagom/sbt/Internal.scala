/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.sbt

import sbt._
import play.sbt.PlayInteractionMode

object Internal {
  object Configs {
    val DevRuntime = config("dev-mode").hide extend Runtime
  }

  object Keys {
    val interactionMode = SettingKey[PlayInteractionMode]("interactionMode", "Hook to configure how a service blocks when running")
    val stop = TaskKey[Unit]("stop", "Stop services, if have been started in non blocking mode")
  }
}
