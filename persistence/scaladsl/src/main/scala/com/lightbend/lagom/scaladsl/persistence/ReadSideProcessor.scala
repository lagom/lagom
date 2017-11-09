/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import akka.Done
import akka.event.Logging
import akka.stream.scaladsl.Flow

import scala.concurrent.Future
import akka.NotUsed
import akka.persistence.query.{ NoOffset, Offset }

object ReadSideProcessor {
  /**
   * An read side offset processor.
   *
   * This is responsible for the actual read side handling, including handling offsets and the events themselves.
   */
  abstract class ReadSideHandler[Event <: AggregateEvent[Event]] {

    /**
     * Prepare the database for all processors.
     *
     * This will be invoked at system startup. It is guaranteed to only be invoked once at a time across the entire
     * cluster, and so is safe to be used to perform actions like creating tables, that could cause problems if
     * done from multiple nodes.
     *
     * It will be invoked again if it fails, and it may be invoked multiple times as nodes of the cluster go up or
     * down. Unless the entire system is restarted, there is no way to guarantee that it will be invoked at a
     * particular time - in particular, it should not be used for doing upgrades unless the entire system is
     * restarted and a new cluster built from scratch.
     *
     * @return A `Future` that is redeemed when preparation is finished.
     */
    def globalPrepare(): Future[Done] =
      Future.successful(Done)

    /**
     * Prepare this processor.
     *
     * The primary purpose of this method is to load the last offset that was processed, so that read side
     * processing can continue from that offset.
     *
     * This also provides an opportunity for processors to do any initialisation activities, such as creating or
     * updating database tables, or migrating data.
     *
     * This will be invoked at least once for each tag, and may be invoked multiple times, such as in the event of
     * failure.
     *
     * @param tag The tag to get the offset for.
     * @return A `Future` that is redeemed when preparation is finished.
     */
    def prepare(tag: AggregateEventTag[Event]): Future[Offset] =
      Future.successful(NoOffset);

    /**
     * Flow to handle the events.
     *
     * If the handler does any blocking, this flow should be configured to use a dispatcher that is configured to
     * allow for that blocking.
     */
    def handle(): Flow[EventStreamElement[Event], Done, NotUsed]
  }

}

/**
 * A read side processor.
 *
 * Read side processors consume events produced by [[com.lightbend.lagom.scaladsl.persistence.PersistentEntity]]
 * instances, and update some read side data store that is optimized for queries.
 *
 * The events they consume must be tagged, and a read side is able to consume events of one or more tags. Events are
 * usually tagged according to some supertype of event, for example, events may be tagged as <code>Order</code> events.
 * They may also be tagged according to a hash of the ID of the entity associated with the event - this allows read
 * side event handling to be sharded across many nodes.  Tagging is done using
 * [[com.lightbend.lagom.scaladsl.persistence.AggregateEventTag]].
 *
 * Read side processors are responsible for tracking what events they have already seen. This is done using offsets,
 * which are sequential values associated with each event. Note that end users typically will not need to handle
 * offsets themselves, this will be provided by Lagom support specific to the read side datastore, and end users can
 * just focus on handling the events themselves.
 */
abstract class ReadSideProcessor[Event <: AggregateEvent[Event]] {

  /**
   * Return a [[ReadSideProcessor#ReadSideHandler]] for the given offset type.
   *
   * @return The offset processor.
   */
  def buildHandler(): ReadSideProcessor.ReadSideHandler[Event]

  /**
   * The tags to aggregate.
   *
   * This must return at least one tag to aggregate. Read side processors will be sharded over the cluster by these
   * tags, so if events are tagged by a shard key, the read side processing load can be distributed across the
   * cluster.
   *
   * @return The tags to aggregate.
   */
  def aggregateTags: Set[AggregateEventTag[Event]]

  /**
   * The name of this read side.
   *
   * This name should be unique among the read sides and entity types of the service. By default it is using the
   * short class name of the concrete `ReadSideProcessor` class. Subclasses may override to define other type names.
   * It is wise to override and retain the original name when the class name is changed because this name is used to
   * identify read sides throughout the cluster.
   */
  def readSideName: String =
    Logging.simpleName(getClass)
}
