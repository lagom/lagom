/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.broker.kafka;

import akka.actor.ActorSystem;
import akka.stream.Materializer;
import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactory;
import com.lightbend.lagom.internal.javadsl.broker.kafka.JavadslKafkaTopic;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.ServiceInfo;
import com.lightbend.lagom.javadsl.api.ServiceLocator;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.typesafe.config.Config;
import scala.concurrent.ExecutionContext;

import javax.inject.Inject;

/** Factory for creating topics instances. */
public class KafkaTopicFactory implements TopicFactory {
  private final ServiceInfo serviceInfo;
  private final ActorSystem system;
  private final Materializer materializer;
  private final ExecutionContext executionContext;

  /**
   * @deprecated As of release 1.7.0. Use {@link #KafkaTopicFactory(ServiceInfo, ActorSystem,
   *     Materializer, ExecutionContext)} instead.
   */
  @Deprecated
  public KafkaTopicFactory(
      ServiceInfo serviceInfo,
      ActorSystem system,
      Materializer materializer,
      ExecutionContext executionContext,
      ServiceLocator serviceLocator,
      Config config) {
    this(serviceInfo, system, materializer, executionContext);
  }

  @Inject
  public KafkaTopicFactory(
      ServiceInfo serviceInfo,
      ActorSystem system,
      Materializer materializer,
      ExecutionContext executionContext) {
    this.serviceInfo = serviceInfo;
    this.system = system;
    this.materializer = materializer;
    this.executionContext = executionContext;
  }

  @Override
  public <Message> Topic<Message> create(Descriptor.TopicCall<Message> topicCall) {
    return new JavadslKafkaTopic<>(topicCall, serviceInfo, system, materializer, executionContext);
  }
}
