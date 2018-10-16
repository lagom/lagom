/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence

import scala.reflect.ClassTag

object AggregateEventTag {
  /**
   * Convenience factory method of [[AggregateEventTag]] that uses the
   * class name of the event type as `tag`. Note that it is needed to
   * retain the original tag when the class name is changed because
   * the tag is part of the store event data.
   */
  def apply[Event <: AggregateEvent[Event]: ClassTag](): AggregateEventTag[Event] = {
    val eventType = implicitly[ClassTag[Event]].runtimeClass.asInstanceOf[Class[Event]]
    new AggregateEventTag(eventType, eventType.getName)
  }

  /**
   * Factory method of [[AggregateEventTag]].
   */
  def apply[Event <: AggregateEvent[Event]: ClassTag](tag: String): AggregateEventTag[Event] =
    new AggregateEventTag(implicitly[ClassTag[Event]].runtimeClass.asInstanceOf[Class[Event]], tag)

  /**
   * Create an aggregate event shards tagger.
   *
   * This is a convenience function that uses the name of the class as the tag name. Note that if the class name
   * changes, the tag name must be retained, and so this method will no longer be suitable for use.
   *
   * Events that return this will be tagged with a tag that is based on a hash of the events persistence ID, modulo
   * the number of shards.
   *
   * The <code>numShards</code> should be selected up front, and shouldn't change. If it does change, events for the
   * same entity will be produced by different event streams and handled by different shards in the read side
   * processor, leading to out of order event handling.
   *
   * @param numShards The number of shards.
   * @return The aggregate event shards tagger.
   */
  def sharded[Event <: AggregateEvent[Event]: ClassTag](numShards: Int): AggregateEventShards[Event] = {
    val eventType = implicitly[ClassTag[Event]].runtimeClass.asInstanceOf[Class[Event]]
    sharded[Event](eventType.getName, numShards)
  }

  /**
   * Create a sharded aggregate event tag.
   *
   * Events that return this will be tagged with a tag that is based on a hash of the events persistence ID, modulo
   * the number of shards.
   *
   * The <code>numShards</code> should be selected up front, and shouldn't change. If it does change, events for the
   * same entity will be produced by different event streams and handled by different shards in the read side
   * processor, leading to out of order event handling.
   *
   * @param baseTagName The base name for the tag, this will be combined with the shard number to form the tag name.
   * @param numShards The number of shards.
   * @return The aggregate event shards tagger.
   */
  def sharded[Event <: AggregateEvent[Event]: ClassTag](
    baseTagName: String, numShards: Int
  ): AggregateEventShards[Event] = {
    new AggregateEventShards[Event](
      implicitly[ClassTag[Event]].runtimeClass.asInstanceOf[Class[Event]],
      baseTagName, numShards
    )
  }

  /**
   * Select a shard given the number of shards and the ID of the entity.
   *
   * @param numShards The number of shards.
   * @param entityId The ID of the entity.
   * @return The selected shard number.
   */
  def selectShard(numShards: Int, entityId: String): Int = {
    Math.abs(entityId.hashCode) % numShards
  }

  /**
   * Generate a shard tag according to the base tag name and the shard number.
   *
   * @param baseTagName The base tag name.
   * @param shardNo The shard number.
   * @return The name of the shard tag.
   */
  def shardTag(baseTagName: String, shardNo: Int): String = {
    s"$baseTagName$shardNo"
  }
}

/**
 * Selects a tag for an event.
 *
 * Can either be a static tag, or a sharded tag generator.
 */
sealed trait AggregateEventTagger[Event <: AggregateEvent[Event]] {
  val eventType: Class[Event]
}

/**
 * The base type of [[PersistentEntity]] events may return one of these
 * to make the events available for read-side processing.
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
) extends AggregateEventTagger[Event] {

  override def toString: String = s"AggregateEventTag($eventType, $tag)"

  override def equals(other: Any): Boolean = other match {
    case that: AggregateEventTag[_] => tag == that.tag
    case _                          => false
  }

  override def hashCode(): Int = tag.hashCode
}

/**
 * The base type of [[PersistentEntity]] events may return one of these
 * to make the events available for sharded read-side processing.
 *
 * The `tag` should be unique among the event types of the service.
 *
 * The `numShards` should be stable and never change.
 *
 * The class name can be used as `tag`, but note that it is needed
 * to retain the original tag when the class name is changed because
 * the tag is part of the store event data.
 */
final class AggregateEventShards[Event <: AggregateEvent[Event]](
  val eventType: Class[Event],
  val tag:       String,
  val numShards: Int
) extends AggregateEventTagger[Event] {

  /**
   * Get the tag for the given entity ID.
   *
   * @param entityId The entity ID to get the tag for.
   * @return The tag.
   */
  def forEntityId(entityId: String): AggregateEventTag[Event] = new AggregateEventTag(
    eventType,
    AggregateEventTag.shardTag(tag, AggregateEventTag.selectShard(numShards, entityId))
  )

  /**
   * @return all the tags that this app will use according to the `numShards` and the `eventType`
   */
  val allTags: Set[AggregateEventTag[Event]] = {
    (for (shardNo <- 0 until numShards) yield new AggregateEventTag(
      eventType,
      AggregateEventTag.shardTag(tag, shardNo)
    )).toSet
  }

  override def toString: String = s"AggregateEventShards($eventType, $tag)"

  override def equals(other: Any): Boolean = other match {
    case that: AggregateEventShards[_] => tag == that.tag
    case _                             => false
  }

  override def hashCode(): Int = tag.hashCode

}
