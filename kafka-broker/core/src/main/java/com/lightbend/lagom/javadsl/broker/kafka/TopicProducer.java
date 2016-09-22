/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.broker.kafka;

import java.util.function.Function;

import com.google.inject.Inject;
import com.lightbend.lagom.internal.broker.kafka.SingletonTopicProducer;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.persistence.Offset;

import akka.NotUsed;
import akka.japi.Pair;
import akka.stream.javadsl.Source;

/**
 * Provides functionality for publishing events of a read-side event stream to Kafka.
 */
public final class TopicProducer {

  @Inject()
  private TopicProducer() {}

  public <Message> Topic<Message> singletonAtLeastOnce(
    Function<Offset, Source<Pair<Message, Offset>, NotUsed>> eventStream) {
    return new SingletonTopicProducer<>(eventStream);
  }

}
