/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.persistence

import com.lightbend.lagom.internal.persistence.PersistenceConfig
import javax.inject.{ Inject, Provider, Singleton }
import play.api.Configuration

@Singleton
class PersistenceConfigProvider @Inject() (configuration: Configuration) extends Provider[PersistenceConfig] {
  override lazy val get = PersistenceConfig(configuration.underlying.getConfig("lagom.persistence"))
}
