/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.impl

import java.time.Instant

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.scaladsl.ReplyEffect
import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import com.lightbend.lagom.scaladsl.persistence.AkkaTaggerAdapter
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import play.api.libs.json.Format
import play.api.libs.json._

import scala.collection.immutable.Seq

/**
 * The current state held by the persistent entity.
 */
case class ShoppingCart(items: Map[String, Int], checkedOutTime: Option[Instant] = None) {

  def checkedOut: Boolean = checkedOutTime.nonEmpty

  //#akka-persistence-command-handler
  def applyCommand(cmd: ShoppingCartCommand): ReplyEffect[ShoppingCartEvent, ShoppingCart] =
    cmd match {
      case x: UpdateItem => onUpdateItem(x)
      case x: Checkout   => onCheckout(x)
      case x: Get        => onReadState(x)
    }

  def applyEvent(evt: ShoppingCartEvent): ShoppingCart =
    evt match {
      case ItemUpdated(productId, quantity) => updateItem(productId, quantity)
      case CheckedOut                       => checkout
    }
  //#akka-persistence-command-handler

  private def onUpdateItem(cmd: UpdateItem): ReplyEffect[ShoppingCartEvent, ShoppingCart] =
    cmd match {
      case UpdateItem(_, quantity, replyTo) if quantity < 0 =>
        Effect.reply(replyTo)(Rejected("Quantity must be greater than zero"))

      case UpdateItem(productId, 0, replyTo) if !items.contains(productId) =>
        Effect.reply(replyTo)(Rejected("Cannot delete item that is not already in cart"))

      case UpdateItem(productId, quantity, replyTo) =>
        Effect
          .persist(ItemUpdated(productId, quantity))
          .thenReply(replyTo)(updatedCart => Accepted(toSummary(updatedCart)))
    }

  //#akka-persistence-typed-example-command-handler
  private def onCheckout(cmd: Checkout): ReplyEffect[ShoppingCartEvent, ShoppingCart] =
    if (items.isEmpty)
      Effect.reply(cmd.replyTo)(Rejected("Cannot checkout empty cart"))
    else
      Effect
        .persist(CheckedOut)
        .thenReply(cmd.replyTo)(updatedCart => Accepted(toSummary(updatedCart)))
  //#akka-persistence-typed-example-command-handler

  private def onReadState(cmd: Get): ReplyEffect[ShoppingCartEvent, ShoppingCart] =
    Effect.reply(cmd.replyTo)(toSummary(this))


  private def updateItem(productId: String, quantity: Int): ShoppingCart = {
    quantity match {
      case 0 => copy(items = items - productId)
      case _ => copy(items = items + (productId -> quantity))
    }
  }

  private def toSummary(shoppingCart: ShoppingCart): Summary = {
    Summary(this.items, this.checkedOutTime.isDefined)
  }

  private def checkout: ShoppingCart = copy(checkedOutTime = Some(Instant.now()))
}

//#akka-persistence-shopping-cart-object
object ShoppingCart {

  val typeKey = EntityTypeKey[ShoppingCartCommand]("ShoppingCartEntity")

  def empty: ShoppingCart = ShoppingCart(items = Map.empty)

  //#akka-persistence-typed-lagom-tagger-adapter
  def behavior(entityContext: EntityContext[ShoppingCartCommand]): Behavior[ShoppingCartCommand] = {
    //#akka-persistence-behavior-definition
    EventSourcedBehavior
      .withEnforcedReplies[ShoppingCartCommand, ShoppingCartEvent, ShoppingCart](
        persistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
        emptyState = ShoppingCart.empty,
        commandHandler = (cart, cmd) => cart.applyCommand(cmd),
        eventHandler = (cart, evt) => cart.applyEvent(evt)
      )
    //#akka-persistence-behavior-definition
      .withTagger(AkkaTaggerAdapter.fromLagom(entityContext, ShoppingCartEvent.Tag))
  }
  //#akka-persistence-typed-lagom-tagger-adapter

  implicit val format: Format[ShoppingCart] = Json.format
}
//#akka-persistence-shopping-cart-object

sealed trait ShoppingCartEvent extends AggregateEvent[ShoppingCartEvent] {
  def aggregateTag = ShoppingCartEvent.Tag
}

object ShoppingCartEvent {
  val Tag = AggregateEventTag.sharded[ShoppingCartEvent](numShards = 10)
}

final case class ItemUpdated(productId: String, quantity: Int) extends ShoppingCartEvent

object ItemUpdated {
  implicit val format: Format[ItemUpdated] = Json.format
}

final case object CheckedOut extends ShoppingCartEvent {
  implicit val format: Format[CheckedOut.type] = Format(
    Reads(_ => JsSuccess(CheckedOut)),
    Writes(_ => Json.obj())
  )
}

// contrary to the common practice to define case class and companion objects
// close to each other, we group them here by case class because we don't want to
// expose the serializers on the documentation. The serializers are not relevant in that case.
//#akka-persistence-typed-replies
sealed trait Confirmation
case class Accepted(summary: Summary) extends Confirmation
case class Rejected(reason: String) extends Confirmation
//#akka-persistence-typed-replies

case object Confirmation {
  implicit val format: Format[Confirmation] = new Format[Confirmation] {
    override def reads(json: JsValue): JsResult[Confirmation] = {
      if ((json \ "reason").isDefined)
        Json.fromJson[Rejected](json)
      else
        Json.fromJson[Accepted](json)
    }

    override def writes(o: Confirmation): JsValue = {
      o match {
        case acc: Accepted => Json.toJson(acc)
        case rej: Rejected => Json.toJson(rej)
      }
    }
  }
}

case object Accepted {
  implicit val format: Format[Accepted] = Json.format
}


object Rejected {
  implicit val format: Format[Rejected] = Json.format
}

final case class Summary(items: Map[String, Int], checkedOut: Boolean = false)
object Summary {
  implicit val format: Format[Summary] = Json.format
}

//#akka-jackson-serialization-marker-trait
/**
 * This is a marker trait for shopping cart commands.
 * We will serialize them using the Akka Jackson serializer that is able to
 * deal with the replyTo field. See application.conf
 */
trait ShoppingCartCommandSerializable
//#akka-jackson-serialization-marker-trait

//#akka-jackson-serialization-command-after
sealed trait ShoppingCartCommand extends ShoppingCartCommandSerializable
case class UpdateItem(productId: String, quantity: Int, replyTo: ActorRef[Confirmation])
    extends ShoppingCartCommand
//#akka-jackson-serialization-command-after

case class Get(replyTo: ActorRef[Summary]) extends ShoppingCartCommand

case class Checkout(replyTo: ActorRef[Confirmation]) extends ShoppingCartCommand

object ShoppingCartSerializerRegistry extends JsonSerializerRegistry {
  //#akka-jackson-serialization-registry-after
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    // state and events can use play-json, but commands should use jackson because of ActorRef[T] (see application.conf)
    JsonSerializer[ShoppingCart],
    JsonSerializer[ItemUpdated],
    JsonSerializer[CheckedOut.type],
    // the replies use play-json as well
    JsonSerializer[Summary],
    JsonSerializer[Confirmation],
    JsonSerializer[Accepted],
    JsonSerializer[Rejected]
  )
  //#akka-jackson-serialization-registry-after
}
