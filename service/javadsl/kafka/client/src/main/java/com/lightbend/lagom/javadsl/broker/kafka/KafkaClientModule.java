/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.broker.kafka;

import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactory;
import play.api.Configuration;
import play.api.Environment;
import play.api.inject.Binding;
import play.api.inject.Module;
import scala.collection.Seq;

public class KafkaClientModule extends Module {

    @Override
    public Seq<Binding<?>> bindings(Environment environment, Configuration configuration) {
        return seq(
            bind(TopicFactory.class).to(KafkaTopicFactory.class)
        );
    }
}
