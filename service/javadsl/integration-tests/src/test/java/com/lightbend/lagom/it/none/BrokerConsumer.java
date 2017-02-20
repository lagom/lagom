/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it.none;

import akka.Done;
import akka.stream.javadsl.Flow;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 *
 */
public class BrokerConsumer {

    @Inject
    public BrokerConsumer(PublisherService publisherService) {
        publisherService.messages().subscribe().atLeastOnce(Flow.<String>create().mapAsync(1, this::doNothing));
    }

    private CompletionStage<Done> doNothing(String msg) {
        return CompletableFuture.completedFuture(Done.getInstance());
    }
}
