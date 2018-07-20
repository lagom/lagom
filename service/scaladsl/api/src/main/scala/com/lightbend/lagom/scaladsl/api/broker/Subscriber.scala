/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.api.broker

import akka.Done
import akka.stream.scaladsl.{ Flow, Source }

import scala.concurrent.Future

/**
 * A Subscriber for consuming messages from a message broker.
 */
trait Subscriber[Payload] {

  /**
   * Returns a copy of this subscriber with the passed group id.
   *
   * @param groupId The group id to assign to this subscriber.
   * @return A copy of this subscriber with the passed group id.
   */
  def withGroupId(groupId: String): Subscriber[Payload]

  /**
   * Returns this subscriber, but message payloads are wrapped
   * in [[Message]] instances to allow accessing any metadata
   * associated with the message.
   *
   * @return A copy of this subscriber.
   */
  def withMetadata: Subscriber[Message[Payload]]

  /**
   * Returns a stream of messages with at most once delivery semantic.
   *
   * If a failure occurs (e.g., an exception is thrown), the user is responsible
   * of deciding how to recover from it (e.g., restarting the stream, aborting, ...).
   */
  def atMostOnceSource: Source[Payload, _]

  /**
   * Applies the passed `flow` to the messages processed by this subscriber. Messages are delivered to the passed
   * `flow` at least once.
   *
   * If a failure occurs (e.g., an exception is thrown), the stream may be automatically restarted starting with the
   * message that caused the failure.
   *
   * Whether the stream is automatically restarted depends on the Lagom message broker implementation in use. If the
   * Kafka Lagom message broker module is being used, then by default the stream is automatically restarted when a
   * failure occurs.
   *
   * The `flow` may pull more elements from upstream but it must emit exactly one `Done` message for each message that
   * it receives. It must also emit them in the same order that the messages were received. This means that the `flow`
   * must not filter or collect a subset of the messages, instead it must split the messages into separate streams and
   * map those that would have been dropped to `Done`.
   *
   * @param flow The flow to apply to each received message.
   * @return A `Future` that will be completed if the `flow` completes.
   */
  def atLeastOnce(flow: Flow[Payload, Done, _]): Future[Done]
}

object Subscriber {

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
  trait GroupId {
    def groupId: String
  }

}
