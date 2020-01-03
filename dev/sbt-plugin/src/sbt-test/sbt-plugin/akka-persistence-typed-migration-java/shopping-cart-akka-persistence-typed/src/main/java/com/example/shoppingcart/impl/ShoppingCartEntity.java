/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.impl;

import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.*;
import com.example.shoppingcart.impl.ShoppingCartCommand.Checkout;
import com.example.shoppingcart.impl.ShoppingCartCommand.Get;
import com.example.shoppingcart.impl.ShoppingCartCommand.UpdateItem;
import com.example.shoppingcart.impl.ShoppingCartEvent.CheckedOut;
import com.example.shoppingcart.impl.ShoppingCartEvent.ItemUpdated;
import com.lightbend.lagom.javadsl.persistence.AkkaTaggerAdapter;
import java.util.Set;

// #akka-persistence-behavior-definition
public class ShoppingCartEntity
    extends EventSourcedBehaviorWithEnforcedReplies<ShoppingCartCommand, ShoppingCartEvent, ShoppingCart>
// #akka-persistence-behavior-definition
     {

    // #akka-persistence-shopping-cart-object
    public static EntityTypeKey<ShoppingCartCommand> ENTITY_TYPE_KEY =
        EntityTypeKey
            .create(ShoppingCartCommand.class, "ShoppingCartEntity");
    // #akka-persistence-shopping-cart-object

    final private EntityContext<ShoppingCartCommand> entityContext;
    final private String entityId;

    public static ShoppingCartEntity behavior(EntityContext<ShoppingCartCommand> entityContext) {
        return new ShoppingCartEntity(entityContext);
    }

    // #shopping-cart-constructor
    ShoppingCartEntity(EntityContext<ShoppingCartCommand> entityContext) {
        super(
            PersistenceId.of(
                entityContext.getEntityTypeKey().name(),
                entityContext.getEntityId(),
                "" // separator must be an empty String - Lagom Java doesn't have a separator
            )
        );
        this.entityContext = entityContext;
        this.entityId = entityContext.getEntityId();
    }
    // #shopping-cart-constructor

    @Override
    public ShoppingCart emptyState() {
        return ShoppingCart.EMPTY;
    }

    @Override
    public CommandHandlerWithReply<ShoppingCartCommand, ShoppingCartEvent, ShoppingCart> commandHandler() {

        CommandHandlerWithReplyBuilder<ShoppingCartCommand, ShoppingCartEvent, ShoppingCart> builder = newCommandHandlerWithReplyBuilder();

        // #akka-persistence-typed-example-command-handler
        builder.forState(ShoppingCart::open)
            .onCommand(UpdateItem.class, (state, cmd) -> {
                if (cmd.quantity < 0) {
                    return Effect().reply(cmd.replyTo, new ShoppingCartCommand.Rejected("Quantity must be greater than zero"));
                } else if (cmd.quantity == 0 && !state.items.containsKey(cmd.productId)) {
                    return Effect().reply(cmd.replyTo, new ShoppingCartCommand.Rejected("Cannot delete item that is not already in cart"));
                } else {
                    return Effect()
                        .persist(new ItemUpdated(entityId, cmd.productId, cmd.quantity))
                        .thenReply(cmd.replyTo, updatedCart -> new ShoppingCartCommand.Accepted(toSummary(updatedCart)));
                }

            })
        // #akka-persistence-typed-example-command-handler
            .onCommand(Checkout.class, (state, cmd) -> {
                if (state.items.isEmpty()) {
                    return Effect().reply(cmd.replyTo, new ShoppingCartCommand.Rejected("Cannot checkout empty cart"));
                } else {
                    return Effect().persist(new CheckedOut(entityId)).thenReply(cmd.replyTo, updatedCart -> new ShoppingCartCommand.Accepted(toSummary(updatedCart)));
                }
            });


        builder.forState(ShoppingCart::isCheckedOut)
            .onCommand(
                UpdateItem.class,
                cmd -> Effect().reply(cmd.replyTo, new ShoppingCartCommand.Rejected("Can't update item on already checked out shopping cart")))
            .onCommand(
                Checkout.class,
                cmd -> Effect().reply(cmd.replyTo, new ShoppingCartCommand.Rejected("Can't checkout on already checked out shopping cart")));

        builder.forAnyState()
            .onCommand(
                Get.class,
                (state, cmd) -> Effect().reply(cmd.replyTo, toSummary(state)));

        return builder.build();
    }

    private Summary toSummary(ShoppingCart shoppingCart) {
        return new Summary(shoppingCart.items, shoppingCart.checkedOut);
    }

    @Override
    public EventHandler<ShoppingCart, ShoppingCartEvent> eventHandler() {
        EventHandlerBuilder<ShoppingCart, ShoppingCartEvent> builder = newEventHandlerBuilder();

        builder.forState(ShoppingCart::open)
            .onEvent(
                ItemUpdated.class,
                (state, evt) -> state.updateItem(evt.productId, evt.quantity))
            .onEvent(
                CheckedOut.class,
                (state, evt) -> state.checkout());


        return builder.build();
    }


    // #akka-persistence-typed-lagom-tagger-adapter
    @Override
    public Set<String> tagsFor(ShoppingCartEvent shoppingCartEvent) {
        return AkkaTaggerAdapter.fromLagom(entityContext, ShoppingCartEvent.TAG).apply(shoppingCartEvent);
    }
    // #akka-persistence-typed-lagom-tagger-adapter
}
