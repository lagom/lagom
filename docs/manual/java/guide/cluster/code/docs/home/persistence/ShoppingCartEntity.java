/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

import akka.actor.typed.ActorRef;
import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.javadsl.persistence.AkkaTaggerAdapter;
import com.lightbend.lagom.serialization.CompressedJsonable;
import com.lightbend.lagom.serialization.Jsonable;

import lombok.Value;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

// #shopping-cart-entity
public class ShoppingCartEntity
    extends EventSourcedBehaviorWithEnforcedReplies<
        ShoppingCartEntity.Command, ShoppingCartEntity.Event, ShoppingCartEntity.ShoppingCart>
// #shopping-cart-entity
{

  private final String cartId;

  // #shopping-cart-akka-tagger
  private final Function<Event, Set<String>> tagger;

  @Override
  public Set<String> tagsFor(Event event) {
    return tagger.apply(event);
  }
  // #shopping-cart-akka-tagger

  // #shopping-cart-type-key
  static EntityTypeKey<Command> ENTITY_TYPE_KEY =
      EntityTypeKey.create(Command.class, "ShoppingCart");
  // #shopping-cart-type-key

  // #shopping-cart-constructor
  private ShoppingCartEntity(EntityContext<Command> entityContext) {
    super(
        // PersistenceId needs a typeHint (or namespace) and entityId,
        // we take then from the EntityContext
        PersistenceId.of(
            entityContext.getEntityTypeKey().name(), // <- type hint
            entityContext.getEntityId() // <- business id
            ));
    // we keep a copy of cartId because it's used in the events
    this.cartId = entityContext.getEntityId();
    // tagger is constructed from adapter and needs EntityContext
    this.tagger = AkkaTaggerAdapter.fromLagom(entityContext, Event.TAG);
  }

  static ShoppingCartEntity create(EntityContext<Command> entityContext) {
    return new ShoppingCartEntity(entityContext);
  }
  // #shopping-cart-constructor

  // #shopping-cart-empty-state
  @Override
  public ShoppingCart emptyState() {
    return ShoppingCart.EMPTY;
  }
  // #shopping-cart-empty-state

  // #shopping-cart-commands
  interface Command<R> extends Jsonable {}

  @Value
  @JsonDeserialize
  static final class AddItem implements Command<Confirmation>, CompressedJsonable {
    public final String itemId;
    public final int quantity;
    public final ActorRef<Confirmation> replyTo;

    @JsonCreator
    AddItem(String itemId, int quantity, ActorRef<Confirmation> replyTo) {
      this.itemId = Preconditions.checkNotNull(itemId, "itemId");
      this.quantity = quantity;
      this.replyTo = replyTo;
    }
  }

  static final class Get implements Command<Summary> {
    private final ActorRef<Summary> replyTo;

    @JsonCreator
    Get(ActorRef<Summary> replyTo) {
      this.replyTo = replyTo;
    }
  }

  static final class Checkout implements Command<Confirmation> {
    private final ActorRef<Confirmation> replyTo;

    @JsonCreator
    Checkout(ActorRef<Confirmation> replyTo) {
      this.replyTo = replyTo;
    }
  }
  // #shopping-cart-commands

  // #shopping-cart-replies
  interface Reply extends Jsonable {}

  interface Confirmation extends Reply {}

  @Value
  @JsonDeserialize
  static final class Summary implements Reply {

    public final Map<String, Integer> items;
    public final boolean checkedOut;
    public final Optional<Instant> checkoutDate;

    @JsonCreator
    Summary(Map<String, Integer> items, boolean checkedOut, Optional<Instant> checkoutDate) {
      this.items = items;
      this.checkedOut = checkedOut;
      this.checkoutDate = checkoutDate;
    }
  }

  @Value
  @JsonDeserialize
  static final class Accepted implements Confirmation {
    public final Summary summary;

    @JsonCreator
    Accepted(Summary summary) {
      this.summary = summary;
    }
  }

  @Value
  @JsonDeserialize
  static final class Rejected implements Confirmation {
    public final String reason;

    @JsonCreator
    Rejected(String reason) {
      this.reason = reason;
    }
  }
  // #shopping-cart-replies

  // #shopping-cart-events
  public interface Event extends Jsonable, AggregateEvent<Event> {
    // #shopping-cart-event-tag
    /** The tag for shopping cart events used for consuming the journal event stream. */
    AggregateEventShards<Event> TAG = AggregateEventTag.sharded(Event.class, 10);
    // #shopping-cart-event-tag

    @Override
    default AggregateEventTagger<Event> aggregateTag() {
      return TAG;
    }
  }

  @Value
  @JsonDeserialize
  static final class ItemAdded implements Event {
    public final String shoppingCartId;
    public final String itemId;
    public final int quantity;
    public final Instant eventTime;

    @JsonCreator
    ItemAdded(String shoppingCartId, String itemId, int quantity, Instant eventTime) {
      this.shoppingCartId = Preconditions.checkNotNull(shoppingCartId, "shoppingCartId");
      this.itemId = Preconditions.checkNotNull(itemId, "itemId");
      this.quantity = quantity;
      this.eventTime = eventTime;
    }
  }

  @Value
  @JsonDeserialize
  static final class CheckedOut implements Event {

    public final String shoppingCartId;
    public final Instant eventTime;

    @JsonCreator
    CheckedOut(String shoppingCartId, Instant eventTime) {
      this.shoppingCartId = Preconditions.checkNotNull(shoppingCartId, "shoppingCartId");
      this.eventTime = eventTime;
    }
  }
  // #shopping-cart-events

  // #shopping-cart-state
  @Value
  @JsonDeserialize
  static final class ShoppingCart implements CompressedJsonable {

    public final PMap<String, Integer> items;
    public final Optional<Instant> checkoutDate;

    @JsonCreator
    ShoppingCart(PMap<String, Integer> items, Instant checkoutDate) {
      this.items = Preconditions.checkNotNull(items, "items");
      this.checkoutDate = Optional.ofNullable(checkoutDate);
    }

    ShoppingCart removeItem(String itemId) {
      PMap<String, Integer> newItems = items.minus(itemId);
      return new ShoppingCart(newItems, null);
    }

    ShoppingCart updateItem(String itemId, int quantity) {
      PMap<String, Integer> newItems = items.plus(itemId, quantity);
      return new ShoppingCart(newItems, null);
    }

    boolean isEmpty() {
      return items.isEmpty();
    }

    boolean hasItem(String itemId) {
      return items.containsKey(itemId);
    }

    ShoppingCart checkout(Instant when) {
      return new ShoppingCart(items, when);
    }

    boolean isOpen() {
      return !this.isCheckedOut();
    }

    boolean isCheckedOut() {
      return this.checkoutDate.isPresent();
    }

    public static final ShoppingCart EMPTY = new ShoppingCart(HashTreePMap.empty(), null);
  }
  // #shopping-cart-state

  // #shopping-cart-create-behavior-with-snapshots
  @Override
  public RetentionCriteria retentionCriteria() {
    return RetentionCriteria.snapshotEvery(100, 2);
  }
  // #shopping-cart-create-behavior-with-snapshots

  // #shopping-cart-command-handlers
  @Override
  public CommandHandlerWithReply<Command, Event, ShoppingCart> commandHandler() {
    CommandHandlerWithReplyBuilder<Command, Event, ShoppingCart> builder =
        newCommandHandlerWithReplyBuilder();
    builder
        .forState(ShoppingCart::isOpen)
        .onCommand(AddItem.class, this::onAddItem)
        .onCommand(Checkout.class, this::onCheckout);

    builder
        .forState(ShoppingCart::isCheckedOut)
        .onCommand(
            AddItem.class,
            cmd ->
                Effect()
                    .reply(cmd.replyTo, new Rejected("Cannot add an item to a checked-out cart")))
        .onCommand(
            Checkout.class,
            cmd -> Effect().reply(cmd.replyTo, new Rejected("Cannot checkout a checked-out cart")));

    builder.forAnyState().onCommand(Get.class, this::onGet);

    return builder.build();
  }

  private ReplyEffect<Event, ShoppingCart> onAddItem(ShoppingCart shoppingCart, AddItem cmd) {
    if (shoppingCart.hasItem(cmd.getItemId())) {
      return Effect()
          .reply(cmd.replyTo, new Rejected("Item was already added to this shopping cart"));
    } else if (cmd.getQuantity() <= 0) {
      return Effect().reply(cmd.replyTo, new Rejected("Quantity must be greater than zero"));
    } else {
      return Effect()
          .persist(new ItemAdded(cartId, cmd.getItemId(), cmd.getQuantity(), Instant.now()))
          .thenReply(cmd.replyTo, s -> new Accepted(toSummary(s)));
    }
  }

  private ReplyEffect<Event, ShoppingCart> onGet(ShoppingCart shoppingCart, Get cmd) {
    return Effect().reply(cmd.replyTo, toSummary(shoppingCart));
  }

  private ReplyEffect<Event, ShoppingCart> onCheckout(ShoppingCart shoppingCart, Checkout cmd) {
    if (shoppingCart.isEmpty()) {
      return Effect().reply(cmd.replyTo, new Rejected("Cannot checkout empty shopping cart"));
    } else {
      return Effect()
          .persist(new CheckedOut(cartId, Instant.now()))
          .thenReply(cmd.replyTo, s -> new Accepted(toSummary(s)));
    }
  }
  // #shopping-cart-command-handlers

  // #shopping-cart-event-handlers
  @Override
  public EventHandler<ShoppingCart, Event> eventHandler() {
    return newEventHandlerBuilder()
        .forAnyState()
        .onEvent(
            ItemAdded.class,
            (shoppingCart, evt) -> shoppingCart.updateItem(evt.getItemId(), evt.getQuantity()))
        .onEvent(CheckedOut.class, (shoppingCart, evt) -> shoppingCart.checkout(evt.getEventTime()))
        .build();
  }
  // #shopping-cart-event-handlers

  private Summary toSummary(ShoppingCart shoppingCart) {
    return new Summary(
        shoppingCart.getItems(), shoppingCart.isCheckedOut(), shoppingCart.getCheckoutDate());
  }
}
