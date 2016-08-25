/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.broker;

import akka.stream.javadsl.Source;

/**
 * A Publisher for publishing messages to a message broker. 
 *
 * @param <Message> The message type.
 *
 * @note This class is not meant to be extended by client code.
 */
public interface Publisher<Message> {
  /**
   * Pubishes a stream of messages.
   * @param messages The messages to publish.
   */
  void publish(Source<Message, ?> messages);
}
