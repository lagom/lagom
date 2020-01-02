/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.impl;

import com.example.shoppingcart.impl.ShoppingCartCommand.Checkout;
import com.example.shoppingcart.impl.ShoppingCartCommand.Get;
import com.example.shoppingcart.impl.ShoppingCartCommand.UpdateItem;
import com.example.shoppingcart.impl.ShoppingCartEvent.CheckedOut;
import com.example.shoppingcart.impl.ShoppingCartEvent.ItemUpdated;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;

import java.util.Optional;

public class ShoppingCartEntity extends PersistentEntity<ShoppingCartCommand, ShoppingCartEvent, ShoppingCart> {

    @Override
    public Behavior initialBehavior(Optional<ShoppingCart> snapshotState) {

        ShoppingCart state = snapshotState.orElse(ShoppingCart.EMPTY);
        BehaviorBuilder b = newBehaviorBuilder(state);

        if (state.isCheckedOut()) {
            return checkedOut(b);
        } else {
            return openShoppingCart(b);
        }
    }

    private Behavior openShoppingCart(BehaviorBuilder b) {
        // Command handler for the UpdateItem command
        b.setCommandHandler(UpdateItem.class, (cmd, ctx) -> {
            if (cmd.getQuantity() < 0) {
                ctx.commandFailed(new ShoppingCartException("Quantity must be greater than zero"));
                return ctx.done();
            } else if (cmd.getQuantity() == 0 && !state().getItems().containsKey(cmd.getProductId())) {
                ctx.commandFailed(new ShoppingCartException("Cannot delete item that is not already in cart"));
                return ctx.done();
            } else {
                return ctx.thenPersist(new ItemUpdated(entityId(), cmd.getProductId(), cmd.getQuantity()), e -> ctx.reply(toSummary(state())));
            }
        });

        // Command handler for the Checkout command
        b.setCommandHandler(Checkout.class, (cmd, ctx) -> {
            if (state().getItems().isEmpty()) {
                ctx.commandFailed(new ShoppingCartException("Cannot checkout empty cart"));
                return ctx.done();
            } else {
                return ctx.thenPersist(new CheckedOut(entityId()), e -> ctx.reply(toSummary(state())));
            }
        });
        commonHandlers(b);
        return b.build();
    }

    private Behavior checkedOut(BehaviorBuilder b) {
        b.setReadOnlyCommandHandler(UpdateItem.class, (cmd, ctx) ->
            ctx.commandFailed(new ShoppingCartException("Can't update item on already checked out shopping cart"))
        );
        b.setReadOnlyCommandHandler(Checkout.class, (cmd, ctx) ->
            ctx.commandFailed(new ShoppingCartException("Can't checkout on already checked out shopping cart"))
        );
        commonHandlers(b);
        return b.build();
    }

    private void commonHandlers(BehaviorBuilder b) {
        b.setReadOnlyCommandHandler(Get.class, (cmd, ctx) -> ctx.reply(toSummary(state())));

        b.setEventHandler(ItemUpdated.class, itemUpdated ->
            state().updateItem(itemUpdated.getProductId(), itemUpdated.getQuantity()));

        b.setEventHandlerChangingBehavior(CheckedOut.class, e ->
            checkedOut(newBehaviorBuilder(state().checkout())));
    }

    private Summary toSummary(ShoppingCart shoppingCart) {
        return new Summary(shoppingCart.items, shoppingCart.checkedOut);
    }
}
