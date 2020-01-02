/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.broker.kafka;

import akka.actor.ActorSystem;
import akka.stream.Materializer;
import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactory;
import com.lightbend.lagom.internal.broker.kafka.KafkaConfig;
import com.lightbend.lagom.internal.broker.kafka.KafkaConfig$;
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
  private final KafkaConfig kafkaConfig;
  private final ServiceLocator serviceLocator;

  /**
   * @deprecated As of release 1.6.0. Use {@link #KafkaTopicFactory(ServiceInfo, ActorSystem,
   *     Materializer, ExecutionContext, ServiceLocator, Config)} instead.
   */
  @Deprecated
  public KafkaTopicFactory(
      ServiceInfo serviceInfo,
      ActorSystem system,
      Materializer materializer,
      ExecutionContext executionContext,
      ServiceLocator serviceLocator) {
    this(
        serviceInfo,
        system,
        materializer,
        executionContext,
        serviceLocator,
        system.settings().config());
  }

  @Inject
  public KafkaTopicFactory(
      ServiceInfo serviceInfo,
      ActorSystem system,
      Materializer materializer,
      ExecutionContext executionContext,
      ServiceLocator serviceLocator,
      Config config) {
    this.serviceInfo = serviceInfo;
    this.system = system;
    this.materializer = materializer;
    this.executionContext = executionContext;
    this.kafkaConfig = KafkaConfig$.MODULE$.apply(config);
    this.serviceLocator = serviceLocator;
  }

  @Override
  public <Message> Topic<Message> create(Descriptor.TopicCall<Message> topicCall) {
    return new JavadslKafkaTopic<>(
        kafkaConfig,
        topicCall,
        serviceInfo,
        system,
        serviceLocator,
        materializer,
        executionContext);
  }
}
