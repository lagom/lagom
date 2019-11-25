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

    // #akka-persistence-typed-replies
    interface Confirmation {}
    class Accepted implements Confirmation {
        public final Summary summary;

        @JsonCreator
        public Accepted(Summary summary) {
            this.summary = summary;
        }
    }
    class Rejected implements Confirmation {
        public final String reason;

        @JsonCreator
        public Rejected(String reason) {
            this.reason = reason;
        }
    }
    // #akka-persistence-typed-replies

    @SuppressWarnings("serial")
    @JsonDeserialize
    // #akka-jackson-serialization-command-after
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
    // #akka-jackson-serialization-command-after

    @JsonDeserialize
    final class Get implements ShoppingCartCommand {

        public final ActorRef<Summary> replyTo;

        public Get(ActorRef<Summary> replyTo) {
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
