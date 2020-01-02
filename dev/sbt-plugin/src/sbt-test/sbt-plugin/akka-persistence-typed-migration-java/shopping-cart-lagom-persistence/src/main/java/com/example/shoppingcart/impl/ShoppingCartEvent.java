/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventShards;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;
import lombok.Value;


public interface ShoppingCartEvent extends Jsonable, AggregateEvent<ShoppingCartEvent> {

    AggregateEventShards<ShoppingCartEvent> TAG = AggregateEventTag.sharded(ShoppingCartEvent.class, 10);

    @SuppressWarnings("serial")
    @Value
    @JsonDeserialize
    final class ItemUpdated implements ShoppingCartEvent {

        public final String shoppingCartId;
        public final String productId;
        public final int quantity;

        @JsonCreator
        ItemUpdated(String shoppingCartId, String productId, int quantity) {
            this.shoppingCartId = Preconditions.checkNotNull(shoppingCartId, "shoppingCartId");
            this.productId = Preconditions.checkNotNull(productId, "productId");
            this.quantity = quantity;
        }
    }

    @SuppressWarnings("serial")
    @Value
    @JsonDeserialize
    final class CheckedOut implements ShoppingCartEvent {

        public final String shoppingCartId;

        @JsonCreator
        CheckedOut(String shoppingCartId) {
            this.shoppingCartId = Preconditions.checkNotNull(shoppingCartId, "shoppingCartId");
        }
    }

    @Override
    default AggregateEventTagger<ShoppingCartEvent> aggregateTag() {
        return TAG;
    }
}
