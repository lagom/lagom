/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.impl;

import akka.NotUsed;
import com.example.shoppingcart.api.ShoppingCartService;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.BadRequest;
import com.lightbend.lagom.javadsl.api.transport.NotFound;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;


public class ShoppingCartServiceImpl implements ShoppingCartService {

    private final PersistentEntityRegistry persistentEntityRegistry;

    private final ReportRepository reportRepository;

    // #akka-persistence-register-classic
    @Inject
    public ShoppingCartServiceImpl(PersistentEntityRegistry persistentEntityRegistry,
                                   ReportRepository reportRepository) {
        this.persistentEntityRegistry = persistentEntityRegistry;
        this.reportRepository = reportRepository;
        persistentEntityRegistry.register(ShoppingCartEntity.class);
    }
    // #akka-persistence-register-classic

    //#akka-persistence-reffor-before
    private PersistentEntityRef<ShoppingCartCommand> entityRef(String id) {
        return persistentEntityRegistry.refFor(ShoppingCartEntity.class, id);
    }

    @Override
    public ServiceCall<NotUsed, String> get(String id) {
        return request ->
            entityRef(id)
                .ask(ShoppingCartCommand.Get.INSTANCE)
                .thenApply(cart -> asShoppingCartView(id, cart));
    }
    //#akka-persistence-reffor-before

    @Override
    public ServiceCall<NotUsed, String> getReport(String id) {
        return request ->
                reportRepository.findById(id).thenApply(report -> {
                    if (report != null) {
                        if (report.isCheckedOut()) return "checkedout";
                        else return "open";

                    }
                    else
                        throw new NotFound("Couldn't find a shopping cart report for '" + id + "'");
                });
    }

    @Override
    public ServiceCall<NotUsed, String> updateItem(String id, String productId, int qty) {
        return item ->
                convertErrors(
                        entityRef(id)
                                .ask(new ShoppingCartCommand.UpdateItem(productId, qty))
                                .thenApply(cart -> asShoppingCartView(id, cart))
                );
    }

    @Override
    public ServiceCall<NotUsed, String> checkout(String id) {
        return request ->
                convertErrors(
                        entityRef(id)
                                .ask(ShoppingCartCommand.Checkout.INSTANCE)
                                .thenApply(cart -> asShoppingCartView(id, cart))
                );
    }


    private <T> CompletionStage<T> convertErrors(CompletionStage<T> future) {
        return future.exceptionally(ex -> {
            if (ex instanceof ShoppingCartException) {
                throw new BadRequest(ex.getMessage());
            }
            else {
                throw new BadRequest("Error updating shopping cart");
            }
        });
    }

    private String asShoppingCartView(String id, Summary summary) {
        StringBuilder builder = new StringBuilder();
        // abc:foo=10:checkedout
        builder.append(id);

        summary.items.forEach((key, value) -> builder
            .append(":")
            .append(key)
            .append("=")
            .append(value));


        if (summary.checkedOut) builder.append(":checkedout");
        else builder.append(":open");

        return builder.toString();
    }

}
