/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

import com.lightbend.lagom.scaladsl.persistence.AggregateEventShards
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag

class BlogEventTag {

  //#aggregate-tag
  object BlogEvent {
    val BlogEventTag = AggregateEventTag[BlogEvent]
  }

  sealed trait BlogEvent extends AggregateEvent[BlogEvent] {
    override def aggregateTag: AggregateEventTag[BlogEvent] =
      BlogEvent.BlogEventTag
  }
  //#aggregate-tag
}
