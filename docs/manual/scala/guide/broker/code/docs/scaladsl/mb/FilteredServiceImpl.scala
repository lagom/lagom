/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.mb

import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.EventStreamElement
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import scala.collection.immutable

/**
 * Implementation of the HelloService.
 */
class FilteredServiceImpl(persistentEntityRegistry: PersistentEntityRegistry)
    extends HelloServiceImpl(persistentEntityRegistry) {

  //#filter-events
  override def greetingsTopic(): Topic[GreetingMessage] =
    TopicProducer.singleStreamWithOffset { fromOffset =>
      persistentEntityRegistry
        .eventStream(HelloEventTag.INSTANCE, fromOffset)
        .mapConcat(filterEvents)
    }

  private def filterEvents(ev: EventStreamElement[HelloEvent]) = ev match {
    // Only publish greetings where the message is "Hello".
    case ev @ EventStreamElement(_, GreetingMessageChanged("Hello"), offset) =>
      immutable.Seq((convertEvent(ev), offset))
    case _ => Nil
  }
  //#filter-events

  private def convertEvent(helloEvent: EventStreamElement[HelloEvent]): GreetingMessage = {
    helloEvent.event match {
      case GreetingMessageChanged(msg) => GreetingMessage(msg)
    }
  }
}
