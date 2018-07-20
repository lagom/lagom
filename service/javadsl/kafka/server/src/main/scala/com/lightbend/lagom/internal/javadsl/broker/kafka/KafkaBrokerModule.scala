/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.broker.kafka

import play.api.{ Configuration, Environment }
import play.api.inject.{ Binding, Module }

class KafkaBrokerModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[JavadslRegisterTopicProducers].toSelf.eagerly()
  )
}
