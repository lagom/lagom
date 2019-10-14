/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.impl;

import akka.cluster.sharding.typed.javadsl.EntityContext;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.javadsl.*;
import com.example.shoppingcart.impl.ShoppingCartCommand.Checkout;
import com.example.shoppingcart.impl.ShoppingCartCommand.Get;
import com.example.shoppingcart.impl.ShoppingCartCommand.UpdateItem;
import com.example.shoppingcart.impl.ShoppingCartEvent.CheckedOut;
import com.example.shoppingcart.impl.ShoppingCartEvent.ItemUpdated;
import com.lightbend.lagom.javadsl.persistence.AkkaTaggerAdapter;

import java.util.Set;

public class ShoppingCartEntity extends EventSourcedBehaviorWithEnforcedReplies<ShoppingCartCommand, ShoppingCartEvent, ShoppingCartState> {

    public static EntityTypeKey<ShoppingCartCommand> ENTITY_TYPE_KEY =
        EntityTypeKey
            .create(ShoppingCartCommand.class, "ShoppingCartEntity")
            .withEntityIdSeparator("");


    final private EntityContext<ShoppingCartCommand> entityContext;
    final private String entityId;

    public static ShoppingCartEntity behavior(EntityContext<ShoppingCartCommand> entityContext) {
        return new ShoppingCartEntity(entityContext);
    }

    ShoppingCartEntity(EntityContext<ShoppingCartCommand> entityContext) {
        super(ENTITY_TYPE_KEY.persistenceIdFrom(entityContext.getEntityId()));
        this.entityContext = entityContext;
        this.entityId = entityContext.getEntityId();
    }


    @Override
    public ShoppingCartState emptyState() {
        return ShoppingCartState.EMPTY;
    }

    @Override
    public CommandHandlerWithReply<ShoppingCartCommand, ShoppingCartEvent, ShoppingCartState> commandHandler() {

        CommandHandlerWithReplyBuilder<ShoppingCartCommand, ShoppingCartEvent, ShoppingCartState> builder = newCommandHandlerWithReplyBuilder();

        builder.forState(ShoppingCartState::open)
            .onCommand(UpdateItem.class, (state, cmd) -> {
                if (cmd.quantity < 0) {
                    return Effect().reply(cmd, new ShoppingCartCommand.Rejected("Quantity must be greater than zero"));
                } else if (cmd.quantity == 0 && !state.items.containsKey(cmd.productId)) {
                    return Effect().reply(cmd, new ShoppingCartCommand.Rejected("Cannot delete item that is not already in cart"));
                } else {
                    return Effect()
                        .persist(new ItemUpdated(entityId, cmd.productId, cmd.quantity))
                        .thenReply(cmd, __ -> new ShoppingCartCommand.Accepted());
                }

            })
            .onCommand(Checkout.class, (state, cmd) -> {
                if (state.items.isEmpty()) {
                    return Effect().reply(cmd, new ShoppingCartCommand.Rejected("Cannot checkout empty cart"));
                } else {
                    return Effect().persist(new CheckedOut(entityId)).thenReply(cmd, __ -> new ShoppingCartCommand.Accepted());
                }
            });


        builder.forState(ShoppingCartState::isCheckedOut)
            .onCommand(
                UpdateItem.class,
                cmd -> Effect().reply(cmd, new ShoppingCartCommand.Rejected("Can't update item on already checked out shopping cart")))
            .onCommand(
                Checkout.class,
                cmd -> Effect().reply(cmd, new ShoppingCartCommand.Rejected("Can't checkout on already checked out shopping cart")));

        builder.forAnyState()
            .onCommand(
                Get.class,
                (state, cmd) -> Effect().reply(cmd, state));

        return builder.build();
    }

    @Override
    public EventHandler<ShoppingCartState, ShoppingCartEvent> eventHandler() {
        EventHandlerBuilder<ShoppingCartState, ShoppingCartEvent> builder = newEventHandlerBuilder();

        builder.forState(ShoppingCartState::open)
            .onEvent(
                ItemUpdated.class,
                (state, evt) -> state.updateItem(evt.productId, evt.quantity))
            .onEvent(
                CheckedOut.class,
                (state, evt) -> state.checkout());


        return builder.build();
    }


    @Override
    public Set<String> tagsFor(ShoppingCartEvent shoppingCartEvent) {
        return AkkaTaggerAdapter.fromLagom(entityContext, ShoppingCartEvent.TAG).apply(shoppingCartEvent);
    }
}
