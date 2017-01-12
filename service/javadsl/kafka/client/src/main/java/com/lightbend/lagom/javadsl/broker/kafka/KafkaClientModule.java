/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.broker.kafka;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactory;

public class KafkaClientModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(TopicFactory.class).to(KafkaTopicFactory.class);
    }
}
