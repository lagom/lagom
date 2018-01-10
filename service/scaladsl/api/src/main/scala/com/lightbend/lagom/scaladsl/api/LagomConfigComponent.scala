/*
 * Copyright (C) 2017-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api

import com.typesafe.config.Config
import play.api.Configuration

trait LagomConfigComponent {
  def configuration: Configuration
  def config: Config = configuration.underlying
}
