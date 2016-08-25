/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.broker.kafka

import com.google.inject.AbstractModule
import com.lightbend.lagom.javadsl.api.broker.modules

class KafkaModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[KafkaConfig]).to(classOf[KafkaConfig.ConfigImpl])
    bind(classOf[modules.Topics]).to(classOf[Topics])
    bind(classOf[RegisterTopicsPublishers]).asEagerSingleton()
  }

}
