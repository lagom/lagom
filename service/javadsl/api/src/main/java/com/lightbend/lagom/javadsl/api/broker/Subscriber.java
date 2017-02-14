/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
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
 * Note: This class is not meant to be extended by client code.
 */
public interface Subscriber<Message> {

  /**
   * Subscribers with the same group id belong to the same subscriber group.
   * Conceptually you can think of a subscriber group as being a single logical
   * subscriber that happens to be made up of multiple processes.
   *
   * Subscriber groups allow a pool of processes to divide the work of consuming
   * and processing records. These processes can either be running on the same
   * machine or, as is more likely, they can be distributed over many machines
   * to provide scalability and fault tolerance for processing.
   */
  interface GroupId {
    String groupId();
  }

  /**
   * Returns a copy of this subscriber with the passed group id.
   *
   * @param groupId The group id to assign to this subscriber.
   * @return A copy of this subscriber with the passed group id.
   * @throws IllegalArgumentException If the passed group id is illegal.
   */
  Subscriber<Message> withGroupId(String groupId) throws IllegalArgumentException;

  /**
   * Returns a stream of messages with at most once delivery semantic.
   *
   * If a failure occurs (e.g., an exception is thrown), the user is responsible
   * of deciding how to recover from it (e.g., restarting the stream, aborting, ...).  
   */
  Source<Message, ?> atMostOnceSource();

  /**
   * Applies the passed <code>flow</code> to the messages processed by this
   * subscriber. Messages are delivered to the passed <code>flow</code> at least
   * once.
   *
   * If a failure occurs (e.g., an exception is thrown), the stream may be
   * automatically restarted starting with the message that caused the failure.
   *
   * Whether the stream is automatically restarted depends on the Lagom message
   * broker implementation in use. If the Kafka Lagom message broker module is
   * being used, then by default the stream is automatically restarted when a
   * failure occurs.  
   * 
   * @param flow The flow to apply to each received message.
   * @return A <code>CompletionStage</code> that may never complete if messages
   *         go through the passed <code>flow</code> flawlessly. However, the
   *         returned <code>CompletionStage</code> may complete with success if
   *         the passed <code>flow</code> signals cancellation upstream.
   * 
   *         If the returned <code>CompletionStage</code> is completed with a
   *         failure, user-code is responsible of deciding what to do (e.g., it
   *         could retry to process the message that caused the failure, or it
   *         could report an application error).
   */
  CompletionStage<Done> atLeastOnce(Flow<Message, Done, ?> flow);

}
