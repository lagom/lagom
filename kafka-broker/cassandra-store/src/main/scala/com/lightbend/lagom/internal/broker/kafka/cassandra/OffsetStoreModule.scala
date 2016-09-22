/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka.cassandra

import scala.collection.immutable.Seq

import com.lightbend.lagom.internal.broker.kafka.store.OffsetTracker

import OffsetTrackerImpl.OffsetTableConfiguration
import javax.inject.Singleton
import play.api.Configuration
import play.api.Environment
import play.api.inject.Binding
import play.api.inject.Module

class OffsetStoreModule extends Module {

  def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[OffsetTracker].to[OffsetTrackerImpl].in[Singleton],
      bind[OffsetTableConfiguration].toInstance(OffsetTableConfiguration(configuration.underlying))
    )

}
