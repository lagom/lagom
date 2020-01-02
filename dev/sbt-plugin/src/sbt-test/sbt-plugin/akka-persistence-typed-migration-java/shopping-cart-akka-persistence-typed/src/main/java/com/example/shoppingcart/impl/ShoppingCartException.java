/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.Jsonable;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
@JsonDeserialize
public class ShoppingCartException extends RuntimeException implements Jsonable {
    public final String message;

    @JsonCreator
    public ShoppingCartException(String message) {
        super(message);
        this.message = message;
    }
}
