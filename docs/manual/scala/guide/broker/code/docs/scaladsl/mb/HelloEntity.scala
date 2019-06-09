/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.mb

import java.time.LocalDateTime

import akka.Done
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTagger
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import play.api.libs.json.Format
import play.api.libs.json.Json

import scala.collection.immutable.Seq

class HelloEntity extends PersistentEntity {

  override type Command = HelloCommand[_]
  override type Event   = HelloEvent
  override type State   = HelloState

  override def initialState: HelloState = HelloState("Hello", LocalDateTime.now.toString)

  override def behavior: Behavior = {
    case HelloState(message, _) =>
      Actions()
        .onCommand[UseGreetingMessage, Done] {

          // Command handler for the UseGreetingMessage command
          case (UseGreetingMessage(newMessage), ctx, state) =>
            // In response to this command, we want to first persist it as a
            // GreetingMessageChanged event
            ctx.thenPersist(GreetingMessageChanged(newMessage)) {
              // Then once the event is successfully persisted, we respond with done.
              _ =>
                ctx.reply(Done)
            }

        }
        .onReadOnlyCommand[Hello, String] {

          // Command handler for the Hello command
          case (Hello(name, organization), ctx, state) =>
            // Reply with a message built from the current message, and the name of
            // the person we're meant to say hello to.
            ctx.reply(s"$message, $name!")

        }
        .onEvent {

          // Event handler for the GreetingMessageChanged event
          case (GreetingMessageChanged(newMessage), state) =>
            // We simply update the current state to use the greeting message from
            // the event.
            HelloState(newMessage, LocalDateTime.now().toString)

        }
  }
}

case class HelloState(message: String, timestamp: String)

object HelloState {
  implicit val format: Format[HelloState] = Json.format
}

object HelloEventTag {
  val INSTANCE: AggregateEventTag[HelloEvent] = AggregateEventTag[HelloEvent]()
}

sealed trait HelloEvent extends AggregateEvent[HelloEvent] {
  override def aggregateTag: AggregateEventTagger[HelloEvent] = HelloEventTag.INSTANCE
}

case class GreetingMessageChanged(message: String) extends HelloEvent

object GreetingMessageChanged {
  implicit val format: Format[GreetingMessageChanged] = Json.format
}
sealed trait HelloCommand[R] extends ReplyType[R]

case class UseGreetingMessage(message: String) extends HelloCommand[Done]

object UseGreetingMessage {
  implicit val format: Format[UseGreetingMessage] = Json.format
}

case class Hello(name: String, organization: Option[String]) extends HelloCommand[String]

object Hello {
  implicit val format: Format[Hello] = Json.format
}

object HelloSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    JsonSerializer[UseGreetingMessage],
    JsonSerializer[Hello],
    JsonSerializer[GreetingMessageChanged],
    JsonSerializer[HelloState]
  )
}
