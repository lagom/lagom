/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor

trait BlogEventProcessorInitial {

  //#processor
  class BlogEventProcessor extends ReadSideProcessor[BlogEvent] {

    override def buildHandler(): ReadSideProcessor.ReadSideHandler[BlogEvent] = {
      // TODO build read side handler
      ???
    }

    override def aggregateTags: Set[AggregateEventTag[BlogEvent]] = {
      // TODO return the tag for the events
      ???
    }
  }
  //#processor
}
