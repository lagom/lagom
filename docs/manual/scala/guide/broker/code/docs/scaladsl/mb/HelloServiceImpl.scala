/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.mb

import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.EventStreamElement
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.broker.TopicProducer

/**
 * Implementation of the HelloService.
 */
class HelloServiceImpl(persistentEntityRegistry: PersistentEntityRegistry) extends HelloService {

  override def hello(id: String) = ServiceCall { _ =>
    // Look up the Hello entity for the given ID.
    val ref = persistentEntityRegistry.refFor[HelloEntity](id)

    // Ask the entity the Hello command.
    ref.ask(Hello(id, None))
  }

  override def useGreeting(id: String) = ServiceCall { request =>
    // Look up the Hello entity for the given ID.
    val ref = persistentEntityRegistry.refFor[HelloEntity](id)

    // Tell the entity to use the greeting message specified.
    ref.ask(UseGreetingMessage(request.message))
  }

  //#implement-topic
  override def greetingsTopic(): Topic[GreetingMessage] =
    TopicProducer.singleStreamWithOffset { fromOffset =>
      persistentEntityRegistry
        .eventStream(HelloEventTag.INSTANCE, fromOffset)
        .map(ev => (convertEvent(ev), ev.offset))
    }

  private def convertEvent(helloEvent: EventStreamElement[HelloEvent]): GreetingMessage = {
    helloEvent.event match {
      case GreetingMessageChanged(msg) => GreetingMessage(msg)
    }
  }
  //#implement-topic

}
