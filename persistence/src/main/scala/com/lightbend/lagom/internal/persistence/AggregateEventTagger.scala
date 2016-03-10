/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence

import akka.persistence.journal.Tagged
import akka.persistence.journal.WriteEventAdapter
import com.lightbend.lagom.javadsl.persistence.AggregateEvent

private[lagom] class AggregateEventTagger extends WriteEventAdapter {
  override def toJournal(event: Any): Any = event match {
    case a: AggregateEvent[_] ⇒
      Tagged(event, Set(a.aggregateTag.tag))
    case _ ⇒
      event
  }

  override def manifest(event: Any): String = ""
}
