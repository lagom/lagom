/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.helloworld.impl

import java.time.LocalDateTime

import akka.Done
import com.lightbend.lagom.scaladsl.persistence.AggregateEventShards
import com.lightbend.lagom.scaladsl.persistence.{ PersistentEntity, AggregateEventTag, AggregateEvent }
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import com.lightbend.lagom.scaladsl.playjson.{ JsonSerializer, JsonSerializerRegistry }
import play.api.libs.json.{ Format, Json }

import scala.collection.immutable.Seq

class HelloWorldEntity extends PersistentEntity {

  override type Command = HelloWorldCommand[_]
  override type Event = HelloWorldEvent
  override type State = HelloWorldState

  override def initialState: HelloWorldState = HelloWorldState("Hello", LocalDateTime.now.toString)

  override def behavior: Behavior = {
    case HelloWorldState(message, _) => Actions().onCommand[UseGreetingMessage, Done] {
      case (UseGreetingMessage(newMessage), ctx, state) =>
        ctx.thenPersist(
          GreetingMessageChanged(entityId, newMessage)
        ) { _ =>
          ctx.reply(Done)
        }
    }.onReadOnlyCommand[Hello, String] {
      case (Hello(name), ctx, state) =>
        ctx.reply(s"$message, $name!")
    }.onEvent {
      case (GreetingMessageChanged(_, newMessage), state) =>
        HelloWorldState(newMessage, LocalDateTime.now().toString)
    }
  }
}

case class HelloWorldState(message: String, timestamp: String)
object HelloWorldState {
  implicit val format: Format[HelloWorldState] = Json.format
}

sealed trait HelloWorldEvent extends AggregateEvent[HelloWorldEvent] {
  override def aggregateTag: AggregateEventShards[HelloWorldEvent] = HelloWorldEvent.Tag
}
object HelloWorldEvent {
  val NumShards = 4
  val Tag = AggregateEventTag.sharded[HelloWorldEvent](NumShards)
}

case class GreetingMessageChanged(id:String, message: String) extends HelloWorldEvent
object GreetingMessageChanged {
  implicit val format: Format[GreetingMessageChanged] = Json.format
}

sealed trait HelloWorldCommand[R] extends ReplyType[R]

case class UseGreetingMessage(message: String) extends HelloWorldCommand[Done]
object UseGreetingMessage {
  implicit val format: Format[UseGreetingMessage] = Json.format
}

case class Hello(name: String) extends HelloWorldCommand[String]
object Hello {
  implicit val format: Format[Hello] = Json.format
}

object HelloWorldSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    JsonSerializer[UseGreetingMessage],
    JsonSerializer[Hello],
    JsonSerializer[GreetingMessageChanged],
    JsonSerializer[HelloWorldState]
  )
}
