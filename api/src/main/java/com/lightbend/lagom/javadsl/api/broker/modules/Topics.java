/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.broker.modules;

import com.lightbend.lagom.javadsl.api.Descriptor.TopicCall;
import com.lightbend.lagom.javadsl.api.broker.Topic;

/**
 * Factory for creating topics.
 *
 * @note This class is useful only to create new message broker module implementations, 
 * and should not leak into the user api.
 */
public interface Topics {
  <Message> Topic<Message> of(TopicCall<Message> topicCall);
}
