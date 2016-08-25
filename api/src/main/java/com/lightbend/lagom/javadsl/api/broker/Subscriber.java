/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.broker;

import java.util.concurrent.CompletionStage;

import akka.Done;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;

/**
 * A Subscriber for consuming messages from a message broker.
 *
 * @param <Message> The message type.
 *
 * @note This class is not meant to be extended by client code.
 */
public interface Subscriber<Message> {

  /**
   * Subscribers with the same group id belong to the same group.
   * It is guaranteed that a set of subscribers with the same group id 
   * acts as if it was a single logical subscriber, i.e., each message 
   * is processed only by a single subscriber in the group.   
   */
  public static interface GroupId {
    String groupId();
  }

  /**
   * Thrown when an illegal group id is selected.
   */
  @SuppressWarnings("serial")
  public static class IllegalGroupId extends Exception {
    public IllegalGroupId(String message) {
      super(message);
    }
  }

  /**
   * Returns a copy of this subscriber with the passed group id.
   *
   * @param groupId The group id to assign to this subscriber.
   * @return A copy of this subscriber with the passed group id.
   * @throws IllegalGroupId If the passed group id is illegal.
   */
  Subscriber<Message> withGroupId(String groupId) throws IllegalGroupId;

  /**
   * Returns a stream of messages with at most once delivery semantic.
   * @return A stream of messages with at most once delivery semantic.
   */
  Source<Message, ?> atMostOnceSource();

  /**
   * Applies the passed flow to the messages sent to this subscriber. Messages 
   * are delivered to the flow at least once.
   * @param flow The flow to apply to each received message. 
   * @return A <code>CompletionStage</code> that will complete when all messages are processed.
   */
  // FIXME: But the stream should never complete, so why having a CompletionStage here and not void?
  CompletionStage<Done> atLeastOnce(Flow<Message, ?, ?> flow);

}
