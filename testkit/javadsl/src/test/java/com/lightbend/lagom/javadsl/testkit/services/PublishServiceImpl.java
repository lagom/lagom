/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.testkit.services;

import akka.japi.Pair;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.internal.api.broker.MessageMetadataKey;
import com.lightbend.lagom.javadsl.api.broker.Message;
import com.lightbend.lagom.javadsl.api.broker.Topic;
import com.lightbend.lagom.javadsl.broker.TopicProducer;

import javax.inject.Inject;
import java.util.Collections;

/** */
public class PublishServiceImpl implements PublishService {

  public static final MessageMetadataKey<String> METADATA_KEY =
      MessageMetadataKey.named("test-key");

  @Inject
  public PublishServiceImpl() {}

  @Override
  public Topic<PublishEvent> messageTopic() {
    return TopicProducer.singleStreamWithOffset(
        offset ->
            Source.from(Collections.singletonList(new PublishEvent(23)))
                .map(msg -> Pair.create(msg, offset)));
  }

  @Override
  public Topic<PublishEvent> messageWithMetadataTopic() {
    return TopicProducer.singleStreamWithOffsetAndMetadata(
        offset -> {
          PublishEvent payload = new PublishEvent(23);
          Message<PublishEvent> message = Message.create(payload).add(METADATA_KEY, "value-23");
          return Source.from(Collections.singletonList(message))
              .map(msg -> Pair.create(msg, offset));
        });
  }
}
