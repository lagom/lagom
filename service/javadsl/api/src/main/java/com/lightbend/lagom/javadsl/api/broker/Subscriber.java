/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.api.broker;

import java.util.concurrent.CompletionStage;

import akka.Done;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;

/**
 * A Subscriber for consuming messages from a message broker.
 *
 * @param <Payload> The message type.
 *     <p>Note: This class is not meant to be extended by client code.
 */
public interface Subscriber<Payload> {

  /**
   * Subscribers with the same group id belong to the same subscriber group. Conceptually you can
   * think of a subscriber group as being a single logical subscriber that happens to be made up of
   * multiple processes.
   *
   * <p>Subscriber groups allow a pool of processes to divide the work of consuming and processing
   * records. These processes can either be running on the same machine or, as is more likely, they
   * can be distributed over many machines to provide scalability and fault tolerance for
   * processing.
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
  Subscriber<Payload> withGroupId(String groupId) throws IllegalArgumentException;

  /**
   * Returns this subscriber, but message payloads are wrapped in {@link Message} instances to allow
   * accessing any metadata associated with the message.
   *
   * @return A copy of this subscriber.
   */
  default Subscriber<Message<Payload>> withMetadata() {
    // default implementation for binary compatibility
    Subscriber<Payload> self = this;
    return new Subscriber<Message<Payload>>() {
      @Override
      public Subscriber<Message<Payload>> withGroupId(String groupId)
          throws IllegalArgumentException {
        return self.withGroupId(groupId).withMetadata();
      }

      @Override
      public Source<Message<Payload>, ?> atMostOnceSource() {
        return self.atMostOnceSource().map(Message::create);
      }

      @Override
      public CompletionStage<Done> atLeastOnce(Flow<Message<Payload>, Done, ?> flow) {
        return self.atLeastOnce(Flow.<Payload>create().map(Message::create).via(flow));
      }
    };
  }

  /**
   * Returns a stream of messages with at most once delivery semantic.
   *
   * <p>If a failure occurs (e.g., an exception is thrown), the user is responsible of deciding how
   * to recover from it (e.g., restarting the stream, aborting, ...).
   */
  Source<Payload, ?> atMostOnceSource();

  /**
   * Applies the passed <code>flow</code> to the messages processed by this subscriber. Messages are
   * delivered to the passed <code>flow</code> at least once.
   *
   * <p>If a failure occurs (e.g., an exception is thrown), the stream may be automatically
   * restarted starting with the message that caused the failure.
   *
   * <p>Whether the stream is automatically restarted depends on the Lagom message broker
   * implementation in use. If the Kafka Lagom message broker module is being used, then by default
   * the stream is automatically restarted when a failure occurs.
   *
   * <p>The <code>flow</code> may pull more elements from upstream but it must emit exactly one
   * <code>Done</code> message for each message that it receives. It must also emit them in the same
   * order that the messages were received. This means that the <code>flow</code> must not filter or
   * collect a subset of the messages, instead it must split the messages into separate streams and
   * map those that would have been dropped to <code>Done</code>.
   *
   * @param flow The flow to apply to each received message.
   * @return A <code>CompletionStage</code> that may never complete if messages go through the
   *     passed <code>flow</code> flawlessly. However, the returned <code>CompletionStage</code> may
   *     complete with success if the passed <code>flow</code> signals cancellation upstream.
   *     <p>If the returned <code>CompletionStage</code> is completed with a failure, user-code is
   *     responsible of deciding what to do (e.g., it could retry to process the message that caused
   *     the failure, or it could report an application error).
   */
  CompletionStage<Done> atLeastOnce(Flow<Payload, Done, ?> flow);
}
