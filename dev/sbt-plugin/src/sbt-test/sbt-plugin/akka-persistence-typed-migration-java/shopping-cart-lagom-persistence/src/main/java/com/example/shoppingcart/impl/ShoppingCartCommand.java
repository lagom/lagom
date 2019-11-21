/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.serialization.CompressedJsonable;
import com.lightbend.lagom.serialization.Jsonable;
import lombok.Value;

public interface ShoppingCartCommand extends Jsonable {

    @SuppressWarnings("serial")
    @Value
    @JsonDeserialize
    // #akka-jackson-serialization-command-before
    final class UpdateItem implements ShoppingCartCommand, CompressedJsonable,
        PersistentEntity.ReplyType<Summary> {

        public final String productId;
        public final int quantity;

        @JsonCreator
        UpdateItem(String productId, int quantity) {
            this.productId = Preconditions.checkNotNull(productId, "productId");
            this.quantity = quantity;
        }
    }
    // #akka-jackson-serialization-command-before

    enum Get implements ShoppingCartCommand, PersistentEntity.ReplyType<Summary> {
        INSTANCE
    }

    enum Checkout implements ShoppingCartCommand, PersistentEntity.ReplyType<Summary> {
        INSTANCE
    }
}
