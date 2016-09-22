/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import com.google.inject.AbstractModule
import com.lightbend.lagom.internal.api.broker.TopicFactory

class KafkaModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[TopicFactory]).to(classOf[KafkaTopicFactory])
    bind(classOf[RegisterTopicProducers]).asEagerSingleton()
  }

}
