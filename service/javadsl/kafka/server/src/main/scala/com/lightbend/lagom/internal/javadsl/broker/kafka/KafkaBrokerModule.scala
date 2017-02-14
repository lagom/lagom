/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.broker.kafka

import com.google.inject.AbstractModule

class KafkaBrokerModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[JavadslRegisterTopicProducers]).asEagerSingleton()
  }

}
