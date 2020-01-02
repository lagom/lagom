/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.broker.kafka;

import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactory;
import com.typesafe.config.Config;
import play.inject.Module;

import java.util.Collections;
import java.util.List;

public class KafkaClientModule extends Module {

  @Override
  public List<play.inject.Binding<?>> bindings(play.Environment environment, Config config) {
    return Collections.singletonList(bindClass(TopicFactory.class).to(KafkaTopicFactory.class));
  }
}
