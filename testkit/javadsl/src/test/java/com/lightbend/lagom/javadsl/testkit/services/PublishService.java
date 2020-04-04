/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.testkit.services;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.broker.Topic;

public interface PublishService extends Service {

  String TOPIC_ID = "pub-topic";
  String TOPIC_WITH_METADATA_ID = "pub-topic-metadata";

  Topic<PublishEvent> messageTopic();

  Topic<PublishEvent> messageWithMetadataTopic();

  @Override
  default Descriptor descriptor() {
    return Service.named("publish-service")
        .withTopics(
            Service.topic(TOPIC_ID, this::messageTopic),
            Service.topic(TOPIC_WITH_METADATA_ID, this::messageWithMetadataTopic));
  }
}
