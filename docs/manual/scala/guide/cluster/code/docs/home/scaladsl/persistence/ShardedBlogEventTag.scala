/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

import com.lightbend.lagom.scaladsl.persistence.AggregateEventShards
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag

class ShardedBlogEventTag {
  //#sharded-tags
  object BlogEvent {
    // will produce tags with shard numbers from 0 to 9
    val NumShards = 10
    val Tag       = AggregateEventTag.sharded[BlogEvent](NumShards)
  }

  sealed trait BlogEvent extends AggregateEvent[BlogEvent] {
    override def aggregateTag: AggregateEventShards[BlogEvent] = BlogEvent.Tag
  }
  //#sharded-tags
}
