/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.api;

import akka.Done;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface ShoppingCartService extends Service {

    ServiceCall<NotUsed, String> get(String id);

    ServiceCall<NotUsed, String> getReport(String id);

    ServiceCall<NotUsed, Done> updateItem(String id, String productId, int qty);

    ServiceCall<NotUsed, Done> checkout(String id);


    @Override
    default Descriptor descriptor() {
        return named("shopping-cart")
            .withCalls(
                restCall(Method.GET, "/shoppingcart/:id", this::get),
                restCall(Method.GET, "/shoppingcart/:id/report", this::getReport),
                // for the RESTafarians, my formal apologies but the GET calls below do mutate state
                // we just want an easy way to mutate data from a sbt scripted test, so no POST/PUT here
                restCall(Method.GET, "/shoppingcart/:id/:productId/:num", this::updateItem),
                restCall(Method.GET, "/shoppingcart/:id/checkout", this::checkout)
            )
            .withAutoAcl(true);
    }
}
