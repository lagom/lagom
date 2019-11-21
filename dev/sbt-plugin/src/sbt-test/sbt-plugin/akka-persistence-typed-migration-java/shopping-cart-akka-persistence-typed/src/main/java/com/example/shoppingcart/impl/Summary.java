package com.example.shoppingcart.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.base.Preconditions;
import org.pcollections.PMap;

public class Summary {
    public final PMap<String, Integer> items;
    public final boolean checkedOut;

    @JsonCreator
    public Summary(PMap<String, Integer> items, boolean checkedOut) {
        this.items = Preconditions.checkNotNull(items, "items");
        this.checkedOut = checkedOut;
    }
}
