/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.impl;

import com.google.common.collect.ImmutableMap;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaReadSide;
import org.pcollections.PSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Persistence;

public class ShoppingCartReportProcessor extends ReadSideProcessor<ShoppingCartEvent> {

    private final JpaReadSide jpaReadSide;
    final private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Inject
    public ShoppingCartReportProcessor(JpaReadSide jpaReadSide) {
        this.jpaReadSide = jpaReadSide;
    }


    @Override
    public ReadSideHandler<ShoppingCartEvent> buildHandler() {
        return jpaReadSide
                .<ShoppingCartEvent>builder("shopping-cart-report")
                .setGlobalPrepare(this::createSchema)
                .setEventHandler(ShoppingCartEvent.ItemUpdated.class, this::createReport)
                .setEventHandler(ShoppingCartEvent.CheckedOut.class, this::addCheckout)
                .build();
    }

    private void createSchema(@SuppressWarnings("unused") EntityManager ignored) {
        Persistence.generateSchema("default", ImmutableMap.of("hibernate.hbm2ddl.auto", "update"));
    }


    private void createReport(EntityManager entityManager, ShoppingCartEvent.ItemUpdated evt) {

        logger.debug("Received ItemUpdate event: " + evt);
        if (findReport(entityManager, evt.shoppingCartId) == null) {
            logger.debug("Creating report for CartID: " + evt.shoppingCartId);
            ShoppingCartReport report = new ShoppingCartReport();
            report.setId(evt.shoppingCartId);
            report.setCreated(true);
            entityManager.persist(report);
        }
    }

    private void addCheckout(EntityManager entityManager, ShoppingCartEvent.CheckedOut evt) {
        ShoppingCartReport report = findReport(entityManager, evt.shoppingCartId);

        logger.debug("Received CheckedOut event: " + evt);
        if (report != null) {
            logger.debug("Marking cart as checked out: " + evt.shoppingCartId);
            report.setCheckedOut(true);
            entityManager.persist(report);
        } else {
            throw new RuntimeException("Didn't find cart for checkout. CartID: " + evt.shoppingCartId);
        }
    }
    private ShoppingCartReport findReport(EntityManager entityManager, String cartId) {
        return entityManager.find(ShoppingCartReport.class, cartId);
    }


    @Override
    public PSequence<AggregateEventTag<ShoppingCartEvent>> aggregateTags() {
        return ShoppingCartEvent.TAG.allTags();
    }

}
