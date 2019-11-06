/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

import java.time.Instant

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.RetentionCriteria
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.Effect.reply
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.scaladsl.ReplyEffect

import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.AggregateEventShards
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTagger
import com.lightbend.lagom.scaladsl.persistence.AkkaTaggerAdapter
import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry

import play.api.libs.json.Format
import play.api.libs.json._

import scala.collection.immutable.Seq

object ShoppingCart {
  // #shopping-cart-commands
  trait CommandSerializable

  sealed trait Command extends CommandSerializable

  final case class AddItem(itemId: String, quantity: Int, replyTo: ActorRef[Confirmation]) extends Command

  final case class Checkout(replyTo: ActorRef[Confirmation]) extends Command

  final case class Get(replyTo: ActorRef[Summary]) extends Command
  // #shopping-cart-commands

  // #shopping-cart-replies
  sealed trait Confirmation

  final case class Accepted(summary: Summary) extends Confirmation

  final case class Rejected(reason: String) extends Confirmation

  final case class Summary(items: Map[String, Int], checkedOut: Boolean)
  // #shopping-cart-replies

  // #shopping-cart-replies-formats
  implicit val summaryFormat: Format[Summary]               = Json.format
  implicit val confirmationAcceptedFormat: Format[Accepted] = Json.format
  implicit val confirmationRejectedFormat: Format[Rejected] = Json.format
  implicit val confirmationFormat: Format[Confirmation] = new Format[Confirmation] {
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
  // #shopping-cart-replies-formats

  // #shopping-cart-events-object
  object Event {
    val Tag: AggregateEventShards[Event] = AggregateEventTag.sharded[Event](numShards = 10)
  }
  // #shopping-cart-events-object

  // #shopping-cart-events
  sealed trait Event extends AggregateEvent[Event] {
    override def aggregateTag: AggregateEventTagger[Event] = Event.Tag
  }

  final case class ItemAdded(itemId: String, quantity: Int) extends Event

  final case class CartCheckedOut(eventTime: Instant) extends Event
  // #shopping-cart-events

  // #shopping-cart-events-formats
  implicit val itemAddedFormat: Format[ItemAdded]           = Json.format
  implicit val cartCheckedOutFormat: Format[CartCheckedOut] = Json.format
  // #shopping-cart-events-formats

  // #shopping-cart-empty-state
  val empty: ShoppingCart = ShoppingCart(items = Map.empty)
  // #shopping-cart-empty-state

  // #shopping-cart-entity-type-key
  val typeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("ShoppingCart")
  // #shopping-cart-entity-type-key

  def createSimple(entityContext: EntityContext[Command]): EventSourcedBehavior[Command, Event, ShoppingCart] = {
    // #shopping-cart-create-behavior
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, ShoppingCart](
        persistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
        emptyState = ShoppingCart.empty,
        commandHandler = (cart, cmd) => cart.applyCommand(cmd),
        eventHandler = (cart, evt) => cart.applyEvent(evt)
      )
    // #shopping-cart-create-behavior
  }

  def createWithTagger(entityContext: EntityContext[Command]): Behavior[Command] = {
    // #shopping-cart-create-behavior-with-tagger
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, ShoppingCart](
        persistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
        emptyState = ShoppingCart.empty,
        commandHandler = (cart, cmd) => cart.applyCommand(cmd),
        eventHandler = (cart, evt) => cart.applyEvent(evt)
      )
      .withTagger(AkkaTaggerAdapter.fromLagom(entityContext, Event.Tag))
    // #shopping-cart-create-behavior-with-tagger
  }

  def createSnapshots(entityContext: EntityContext[Command]): Behavior[Command] = {
    // #shopping-cart-create-behavior-with-snapshots
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, ShoppingCart](
        persistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
        emptyState = ShoppingCart.empty,
        commandHandler = (cart, cmd) => cart.applyCommand(cmd),
        eventHandler = (cart, evt) => cart.applyEvent(evt)
      )
      // snapshot every 100 events and keep at most 2 snapshots on db
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2))
    // #shopping-cart-create-behavior-with-snapshots
  }

  // #shopping-cart-apply-behavior-creation
  def apply(entityContext: EntityContext[Command]): Behavior[Command] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, ShoppingCart](
        persistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId),
        emptyState = ShoppingCart.empty,
        commandHandler = (cart, cmd) => cart.applyCommand(cmd),
        eventHandler = (cart, evt) => cart.applyEvent(evt)
      )
      .withTagger(AkkaTaggerAdapter.fromLagom(entityContext, Event.Tag))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 2))
  }
  // #shopping-cart-apply-behavior-creation

  /**
   * The aggregate get snapshoted every configured number of events. This
   * means the state gets stored to the database, so that when the entity gets
   * loaded, you don't need to replay all the events, just the ones since the
   * snapshot. Hence, a JSON format needs to be declared so that it can be
   * serialized and deserialized when storing to and from the database.
   */
  implicit val format: Format[ShoppingCart] = Json.format
}

// #shopping-cart-state
final case class ShoppingCart(
  items: Map[String, Int],
  // checkedOutTime defines if cart was checked-out or not:
  // case None, cart is open
  // case Some, cart is checked-out
  checkedOutTime: Option[Instant] = None
)
// #shopping-cart-state
{
  import ShoppingCart._

  def checkedOut: Boolean = checkedOutTime.nonEmpty

  // #shopping-cart-command-handlers
  def applyCommand(cmd: Command): ReplyEffect[Event, ShoppingCart] =
    if (checkedOut) {
      cmd match {
        case AddItem(_, _, replyTo) => reply(replyTo)(Rejected("Cannot add an item to a checked-out cart"))
        case Checkout(replyTo)      => reply(replyTo)(Rejected("Cannot checkout a checked-out cart"))
        case Get(replyTo)           => onGet(replyTo)
      }
    } else {
      cmd match {
        case AddItem(itemId, quantity, replyTo) => onAddItem(itemId, quantity, replyTo)
        case Checkout(replyTo)                  => onCheckout(replyTo)
        case Get(replyTo)                       => onGet(replyTo)
      }
    }

  private def onAddItem(
      itemId: String,
      quantity: Int,
      replyTo: ActorRef[Confirmation]
  ): ReplyEffect[Event, ShoppingCart] = {
    if (items.contains(itemId))
      Effect.reply(replyTo)(Rejected(s"Item '$itemId' was already added to this shopping cart"))
    else if (quantity <= 0)
      Effect.reply(replyTo)(Rejected("Quantity must be greater than zero"))
    else
      Effect
        .persist(ItemAdded(itemId, quantity))
        .thenReply(replyTo)(updatedCart => Accepted(toSummary(updatedCart)))
  }

  private def onCheckout(replyTo: ActorRef[Confirmation]): ReplyEffect[Event, ShoppingCart] = {
    if (items.isEmpty)
      Effect.reply(replyTo)(Rejected("Cannot checkout an empty shopping cart"))
    else
      Effect
        .persist(CartCheckedOut(Instant.now()))
        .thenReply(replyTo)(updatedCart => Accepted(toSummary(updatedCart)))
  }

  private def onGet(replyTo: ActorRef[Summary]): ReplyEffect[Event, ShoppingCart] = {
    reply(replyTo)(toSummary(shoppingCart = this))
  }

  private def toSummary(shoppingCart: ShoppingCart): Summary = {
    Summary(shoppingCart.items, shoppingCart.checkedOut)
  }

  // #shopping-cart-command-handlers

  // #shopping-cart-state-event-handlers
  def applyEvent(evt: Event): ShoppingCart =
    evt match {
      case ItemAdded(itemId, quantity)    => onItemAdded(itemId, quantity)
      case CartCheckedOut(checkedOutTime) => onCartCheckedOut(checkedOutTime)
    }

  private def onItemAdded(itemId: String, quantity: Int): ShoppingCart =
    copy(items = items + (itemId -> quantity))

  private def onCartCheckedOut(checkedOutTime: Instant): ShoppingCart = {
    copy(checkedOutTime = Option(checkedOutTime))
  }
  // #shopping-cart-state-event-handlers
}

object ShoppingCartSerializerRegistry extends JsonSerializerRegistry {
  import ShoppingCart._

  override def serializers: Seq[JsonSerializer[_]] = Seq(
    // state and events can use play-json, but commands should use jackson because of ActorRef[T] (see application.conf)
    JsonSerializer[ShoppingCart],
    JsonSerializer[ItemAdded],
    JsonSerializer[CartCheckedOut],
    // the replies use play-json as well
    JsonSerializer[Summary],
    JsonSerializer[Confirmation],
    JsonSerializer[Accepted],
    JsonSerializer[Rejected],
  )
}
