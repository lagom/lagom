/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.impl;

import com.example.shoppingcart.api.ShoppingCartService;
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class ShoppingCartModule extends AbstractModule implements ServiceGuiceSupport {
    @Override
    protected void configure() {
        bindService(ShoppingCartService.class, ShoppingCartServiceImpl.class);
        bind(ReportRepository.class);
    }
}
