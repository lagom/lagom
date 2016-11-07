/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import com.google.inject.AbstractModule
import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactory

class KafkaBrokerModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[RegisterTopicProducers]).asEagerSingleton()
  }

}
