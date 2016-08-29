/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import org.pcollections.{ PSequence, TreePVector }

import scala.collection.JavaConverters._

object AggregateEventTag {
  /**
   * Convenience factory method of [[AggregateEventTag]] that uses the
   * class name of the event type as `tag`. Note that it is needed to
   * retain the original tag when the class name is changed because
   * the tag is part of the store event data.
   */
  def of[Event <: AggregateEvent[Event]](eventType: Class[Event]): AggregateEventTag[Event] =
    new AggregateEventTag(eventType, eventType.getName)

  /**
   * Factory method of [[AggregateEventTag]].
   */
  def of[Event <: AggregateEvent[Event]](eventType: Class[Event], tag: String): AggregateEventTag[Event] =
    new AggregateEventTag(eventType, tag)

  /**
   * Create a sharded aggregate event tag.
   *
   * This is a convenience function that uses the name of the class as the tag name. Note that if the class name
   * changes, the tag name must be retained, and so this method will no longer be suitable for use.
   *
   * A tag will be selected based on a hash function that combines the `entityId` and `numShards`.
   *
   * The <code>numShards</code> should be selected up front, and shouldn't change. If it does change, events for the
   * same entity will be produced by different event streams and handled by different shards in the read side
   * processor, leading to out of order event handling.
   *
   * @param eventType The type of the event.
   * @param numShards The number of shards.
   * @param entityId The ID of the entity.
   * @return The aggregate event tag.
   */
  def shard[Event <: AggregateEvent[Event]](eventType: Class[Event], numShards: Int, entityId: String): AggregateEventTag[Event] =
    shard(eventType, eventType.getName, numShards, entityId)

  /**
   * Create a sharded aggregate event tag.
   *
   * A tag will be selected based on a hash function that combines the `entityId` and `numShards`.
   *
   * The <code>numShards</code> should be selected up front, and shouldn't change. If it does change, events for the
   * same entity will be produced by different event streams and handled by different shards in the read side
   * processor, leading to out of order event handling.
   *
   * @param eventType The type of the event.
   * @param baseTagName The base name for the tag, this will be combined with the shard number to form the tag name.
   * @param numShards The number of shards.
   * @param entityId The ID of the entity.
   * @return The aggregate event tag.
   */
  def shard[Event <: AggregateEvent[Event]](eventType: Class[Event], baseTagName: String, numShards: Int, entityId: String): AggregateEventTag[Event] = {
    of(eventType, shardTag(baseTagName, selectShard(numShards, entityId)))
  }

  /**
   * Create a sequence of sharded tags according to the number of shards.
   *
   * This is a convenience function that uses the name of the class as the tag name. Note that if the class name
   * changes, the tag name must be retained, and so this method will no longer be suitable for use.
   *
   * The <code>numShards</code> should be selected up front, and shouldn't change. If it does change, events for the
   * same entity will be produced by different event streams and handled by different shards in the read side
   * processor, leading to out of order event handling.
   *
   * @param eventType The type of the event.
   * @param numShards The number of shards.
   * @return The aggregate event tag.
   */
  def shards[Event <: AggregateEvent[Event]](eventType: Class[Event], numShards: Int): PSequence[AggregateEventTag[Event]] =
    shards(eventType, eventType.getName, numShards)

  /**
   * Create a sequence of sharded tags according to the number of shards.
   *
   * The <code>numShards</code> should be selected up front, and shouldn't change. If it does change, events for the
   * same entity will be produced by different event streams and handled by different shards in the read side
   * processor, leading to out of order event handling.
   *
   * @param eventType The type of the event.
   * @param baseTagName The base tag name.
   * @param numShards The number of shards.
   * @return The aggregate event tag.
   */
  def shards[Event <: AggregateEvent[Event]](eventType: Class[Event], baseTagName: String, numShards: Int): PSequence[AggregateEventTag[Event]] = {
    val shardTags = for (shardNo <- 0 until numShards) yield of(eventType, shardTag(baseTagName, shardNo))
    TreePVector.from(shardTags.asJava)
  }

  /**
   * Select a shard given the number of shards and the ID of the entity.
   *
   * @param numShards The number of shards.
   * @param entityId The ID of the entity.
   * @return The selected shard number.
   */
  def selectShard(numShards: Int, entityId: String) = {
    Math.abs(entityId.hashCode) % numShards
  }

  /**
   * Generate a shard tag according to the base tag name and the shard number.
   *
   * @param baseTagName The base tag name.
   * @param shardNo The shard number.
   * @return The name of the shard tag.
   */
  def shardTag(baseTagName: String, shardNo: Int) = {
    s"$baseTagName$shardNo"
  }
}

/**
 * The base type of [[PersistentEntity]] events may implement this
 * interface to make the events available for read-side processing.
 *
 * The `tag` should be unique among the event types of the service.
 *
 * The class name can be used as `tag`, but note that it is needed
 * to retain the original tag when the class name is changed because
 * the tag is part of the store event data.
 */
final class AggregateEventTag[Event <: AggregateEvent[Event]](
  val eventType: Class[Event],
  val tag:       String
) {

  override def toString = s"AggregateEventTag($eventType, $tag)"

  override def equals(other: Any) = other match {
    case that: AggregateEventTag[_] => tag == that.tag
    case _                          => false
  }

  override def hashCode() = tag.hashCode
}
