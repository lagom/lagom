/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import lombok.Value;

@Value
@JsonDeserialize
public final class ShoppingCartItem {

    public final String productId;
    public final int quantity;

    @JsonCreator
    public ShoppingCartItem(String productId, int quantity) {
        this.productId = Preconditions.checkNotNull(productId, "productId");
        this.quantity = quantity;
    }
}
