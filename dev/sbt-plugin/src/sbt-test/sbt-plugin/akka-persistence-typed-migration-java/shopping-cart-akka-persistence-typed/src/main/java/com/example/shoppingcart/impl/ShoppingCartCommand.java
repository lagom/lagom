/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.impl;

import akka.actor.typed.ActorRef;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import com.lightbend.lagom.serialization.CompressedJsonable;
import com.lightbend.lagom.serialization.Jsonable;

public interface ShoppingCartCommand extends Jsonable {

    interface Confirmation {}
    class Accepted implements Confirmation{}
    class Rejected implements Confirmation{
        public final String reason;

        public Rejected(String reason) {
            this.reason = reason;
        }
    }

    @SuppressWarnings("serial")
    @JsonDeserialize
    final class UpdateItem implements ShoppingCartCommand, CompressedJsonable {
        public final String productId;
        public final int quantity;
        public final ActorRef<Confirmation> replyTo;

        @JsonCreator
        UpdateItem(String productId, int quantity, ActorRef<Confirmation> replyTo) {
            this.productId = Preconditions.checkNotNull(productId, "productId");
            this.quantity = quantity;
            this.replyTo = replyTo;
        }
    }

    @JsonDeserialize
    final class Get implements ShoppingCartCommand {

        public final ActorRef<ShoppingCartState> replyTo;

        public Get(ActorRef<ShoppingCartState> replyTo) {
            this.replyTo = replyTo;
        }
    }

    @JsonDeserialize
    final class Checkout implements ShoppingCartCommand {

        public final ActorRef<Confirmation> replyTo;

        public Checkout(ActorRef<Confirmation> replyTo) {
            this.replyTo = replyTo;
        }
    }
}
