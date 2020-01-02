/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.impl;

import akka.NotUsed;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import com.example.shoppingcart.api.ShoppingCartService;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.BadRequest;
import com.lightbend.lagom.javadsl.api.transport.NotFound;

import javax.inject.Inject;
import java.time.Duration;
import java.util.concurrent.CompletionStage;


public class ShoppingCartServiceImpl implements ShoppingCartService {

    private final ReportRepository reportRepository;

    private final ClusterSharding clusterSharding;

    private final Duration askTimeout = Duration.ofSeconds(5);

    // #akka-persistence-init-sharded-behavior
    @Inject
    public ShoppingCartServiceImpl(ClusterSharding clusterSharding,
                                   ReportRepository reportRepository) {
        this.clusterSharding = clusterSharding;
        this.reportRepository = reportRepository;
        // register entity on shard
        this.clusterSharding.init(
            Entity.of(
                ShoppingCartEntity.ENTITY_TYPE_KEY,
                ShoppingCartEntity::behavior
            )
        );
    }
    // #akka-persistence-init-sharded-behavior


    // #akka-persistence-reffor-after
    private EntityRef<ShoppingCartCommand> entityRef(String id) {
        return clusterSharding.entityRefFor(ShoppingCartEntity.ENTITY_TYPE_KEY, id);
    }

    @Override
    public ServiceCall<NotUsed, String> get(String id) {
        return request ->
            entityRef(id)
                .<Summary>ask(replyTo -> new ShoppingCartCommand.Get(replyTo), askTimeout)
                .thenApply(cart -> asShoppingCartView(id, cart));
    }
    // #akka-persistence-reffor-after

    @Override
    public ServiceCall<NotUsed, String> getReport(String id) {
        return request ->
            reportRepository.findById(id).thenApply(report -> {
                if (report != null)
                    if (report.isCheckedOut()) return "checkedout";
                    else return "open";
                else
                    throw new NotFound("Couldn't find a shopping cart report for '" + id + "'");
            });
    }

    @Override
    public ServiceCall<NotUsed, String> updateItem(String id, String productId, int qty) {
        return item ->
                entityRef(id)
                    .<ShoppingCartCommand.Confirmation>ask(
                    replyTo -> new ShoppingCartCommand.UpdateItem(productId, qty, replyTo), askTimeout
                )
                    .thenApply(this::handleConfirmation)
                    .thenApply(accepted -> asShoppingCartView(id, accepted.summary));
    }

    @Override
    public ServiceCall<NotUsed, String> checkout(String id) {
        return request ->
            entityRef(id)
                    .ask(ShoppingCartCommand.Checkout::new, askTimeout)
                    .thenApply(this::handleConfirmation)
                    .thenApply(accepted -> asShoppingCartView(id, accepted.summary));
    }


    private <T> CompletionStage<T> convertErrors(CompletionStage<T> future) {
        return future.exceptionally(ex -> {
            if (ex instanceof ShoppingCartException) {
                throw new BadRequest(ex.getMessage());
            } else {
                throw new BadRequest("Error updating shopping cart");
            }
        });
    }

    private ShoppingCartCommand.Accepted handleConfirmation(ShoppingCartCommand.Confirmation confirmation) {
        if (confirmation instanceof ShoppingCartCommand.Accepted) {
            ShoppingCartCommand.Accepted accepted = (ShoppingCartCommand.Accepted) confirmation;
            return accepted;
        }

        ShoppingCartCommand.Rejected rejected = (ShoppingCartCommand.Rejected) confirmation;
        throw new BadRequest(rejected.reason);
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
