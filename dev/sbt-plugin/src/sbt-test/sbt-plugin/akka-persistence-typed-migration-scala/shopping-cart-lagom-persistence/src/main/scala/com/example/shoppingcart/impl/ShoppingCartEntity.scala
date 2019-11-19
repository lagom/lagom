/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.impl

import java.time.Instant

import akka.Done
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, PersistentEntity}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import play.api.libs.json._

import scala.collection.immutable.Seq

object ShoppingCartEntity {

  import play.api.libs.functional.syntax._
  val naStringSerializer: Format[Option[String]] = {
    implicitly[Format[String]].inmap(
      str => Some(str).filterNot(_ == "N/A"),
      maybeStr => maybeStr.getOrElse("N/A")
    )
  }
}

class ShoppingCartEntity extends PersistentEntity {

  override type Command = ShoppingCartCommand[_]
  override type Event = ShoppingCartEvent
  override type State = ShoppingCart

  override def initialState: ShoppingCart = ShoppingCart(items = Map.empty)

  override def behavior: Behavior = {
    case ShoppingCart(_, None) => openShoppingCart
    case ShoppingCart(_, Some(_)) => checkedOut
  }

  def openShoppingCart = {
    Actions().onCommand[UpdateItem, Done] {

      // Command handler for the UpdateItem command
      case (UpdateItem(_, quantity), ctx, _) if quantity < 0 =>
        ctx.commandFailed(ShoppingCartException("Quantity must be greater than zero"))
        ctx.done
      case (UpdateItem(productId, 0), ctx, state) if !state.items.contains(productId) =>
        ctx.commandFailed(ShoppingCartException("Cannot delete item that is not already in cart"))
        ctx.done
      case (UpdateItem(productId, quantity), ctx, _) =>
        // In response to this command, we want to first persist it as an ItemUpdated event
        ctx.thenPersist(
          ItemUpdated(productId, quantity)
        ) { _ =>
          // Then once the event is successfully persisted, we respond with done.
          ctx.reply(Done)
        }

    }.onCommand[Checkout.type, Done] {

      // Command handler for the Checkout command
      case (Checkout, ctx, state) if state.items.isEmpty =>
        ctx.commandFailed(ShoppingCartException("Cannot checkout empty cart"))
        ctx.done
      case (Checkout, ctx, _) =>
        // In response to this command, we want to first persist it as a
        // CheckedOut event
        ctx.thenPersist(
          CheckedOut
        ) { _ =>
          // Then once the event is successfully persisted, we respond with done.
          ctx.reply(Done)
        }

    }.onReadOnlyCommand[Get.type, Summary] {

      // Command handler for the Hello command
      case (Get, ctx, state) =>
        // Reply with the current summary.
        ctx.reply(Summary(state.items, state.checkedOutTime.isDefined))

    }.onEvent(eventHandlers)
  }

  def checkedOut = {
    Actions().onReadOnlyCommand[Get.type, Summary] {

      // Command handler for the Hello command
      case (Get, ctx, state) =>
        // Reply with the current summary.
        ctx.reply(Summary(state.items, state.checkedOutTime.isDefined))

    }.onCommand[UpdateItem, Done] {

      // Not valid when checked out
      case (_, ctx, _) =>
        ctx.commandFailed(ShoppingCartException("Can't update item on already checked out shopping cart"))
        ctx.done

    }.onCommand[Checkout.type, Done] {

      // Not valid when checked out
      case (_, ctx, _) =>
        ctx.commandFailed(ShoppingCartException("Can't checkout on already checked out shopping cart"))
        ctx.done

    }.onEvent(eventHandlers)
  }

  def eventHandlers: EventHandler = {
    // Event handler for the ItemUpdated event
    case (ItemUpdated(productId: String, quantity: Int), state) => state.updateItem(productId, quantity)

    // Event handler for the checkout event
    case (CheckedOut, state) => state.checkout
  }
}

case class ShoppingCart(items: Map[String, Int], checkedOutTime: Option[Instant] = None) {

  def updateItem(productId: String, quantity: Int): ShoppingCart = {
    quantity match {
      case 0 => copy(items = items - productId)
      case _ => copy(items = items + (productId -> quantity))
    }
  }

  def checkedOut: Boolean = checkedOutTime.nonEmpty

  def checkout: ShoppingCart = copy(checkedOutTime = Some(Instant.now()))
}

object ShoppingCart {
  implicit val format: Format[ShoppingCart] = Json.format
}

case class Summary(items: Map[String, Int], checkedOut: Boolean = false)
object  Summary {
  implicit val format: Format[Summary] = Json.format
}

sealed trait ShoppingCartEvent extends AggregateEvent[ShoppingCartEvent] {
  def aggregateTag = ShoppingCartEvent.Tag
}

object ShoppingCartEvent {
  val Tag = AggregateEventTag.sharded[ShoppingCartEvent](numShards = 10)
}

case class ItemUpdated(productId: String, quantity: Int) extends ShoppingCartEvent

object ItemUpdated {
  implicit val format: Format[ItemUpdated] = Json.format
}

final case object CheckedOut extends ShoppingCartEvent {
  implicit val format: Format[CheckedOut.type] = Format(
    Reads(_ => JsSuccess(CheckedOut)),
    Writes(_ => Json.obj())
  )
}

//#akka-jackson-serialization-command-before
sealed trait ShoppingCartCommand[R] extends ReplyType[R]

case class UpdateItem(productId: String, quantity: Int) extends ShoppingCartCommand[Done]

object UpdateItem {
  implicit val format: Format[UpdateItem] = Json.format
}
//#akka-jackson-serialization-command-before

case object Get extends ShoppingCartCommand[Summary] {
  implicit val format: Format[Get.type] = Format(
    Reads(_ => JsSuccess(Get)),
    Writes(_ => Json.obj())
  )
}

case object Checkout extends ShoppingCartCommand[Done] {

  implicit val format: Format[Checkout.type] = Format(
    Reads(_ => JsSuccess(Checkout)),
    Writes(_ => Json.obj())
  )
}

case class ShoppingCartException(message: String) extends RuntimeException(message)

object ShoppingCartException {
  implicit val format: Format[ShoppingCartException] = Json.format[ShoppingCartException]
}

object ShoppingCartSerializerRegistry extends JsonSerializerRegistry {
  //#akka-jackson-serialization-registry-before
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    JsonSerializer[ItemUpdated],
    JsonSerializer[CheckedOut.type],
    JsonSerializer[UpdateItem],
    JsonSerializer[Checkout.type],
    JsonSerializer[Get.type],
    JsonSerializer[ShoppingCart],
    JsonSerializer[ShoppingCartException]
  )
  //#akka-jackson-serialization-registry-before
}
