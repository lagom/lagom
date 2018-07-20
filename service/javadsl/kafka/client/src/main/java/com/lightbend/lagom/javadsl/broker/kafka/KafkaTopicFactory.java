/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
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
import scala.concurrent.ExecutionContext;

import javax.inject.Inject;

/**
 * Factory for creating topics instances.
 */
public class KafkaTopicFactory implements TopicFactory {
    private final ServiceInfo serviceInfo;
    private final ActorSystem system;
    private final Materializer materializer;
    private final ExecutionContext executionContext;
    private final KafkaConfig config;
    private final ServiceLocator serviceLocator;

    @Inject
    public KafkaTopicFactory(ServiceInfo serviceInfo, ActorSystem system, Materializer materializer,
            ExecutionContext executionContext, ServiceLocator serviceLocator) {
        this.serviceInfo = serviceInfo;
        this.system = system;
        this.materializer = materializer;
        this.executionContext = executionContext;
        // TODO: expose KafkaConfig as a constructor argument instead of building one from system.settings().config()
        // https://github.com/lagom/lagom/issues/1255
        this.config = KafkaConfig$.MODULE$.apply(system.settings().config());
        this.serviceLocator = serviceLocator;
    }

    @Override
    public <Message> Topic<Message> create(Descriptor.TopicCall<Message> topicCall) {
        return new JavadslKafkaTopic<>(config, topicCall, serviceInfo, system, serviceLocator, materializer, executionContext);
    }
}
