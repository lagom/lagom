/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Preconditions;
import com.lightbend.lagom.serialization.CompressedJsonable;
import lombok.Value;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;

@SuppressWarnings("serial")
@Value
@JsonDeserialize
public final class ShoppingCart implements CompressedJsonable {

    public final PMap<String, Integer> items;
    public final boolean checkedOut;


    @JsonCreator
    ShoppingCart(PMap<String, Integer> items, boolean checkedOut) {
        this.items = Preconditions.checkNotNull(items, "items");
        this.checkedOut = checkedOut;
    }

    public ShoppingCart updateItem(String productId, int quantity) {
        PMap<String, Integer> newItems;
        if (quantity == 0) {
            newItems = items.minus(productId);
        } else {
            newItems = items.plus(productId, quantity);
        }
        return new ShoppingCart(newItems, checkedOut);
    }

    public ShoppingCart checkout() {
        return new ShoppingCart(items, true);
    }

    public boolean open() {
        return !checkedOut;
    }

    public static final ShoppingCart EMPTY = new ShoppingCart(HashTreePMap.empty(), false);
}
