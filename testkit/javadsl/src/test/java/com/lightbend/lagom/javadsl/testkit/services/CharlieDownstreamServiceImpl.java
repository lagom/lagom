/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit.services;

import akka.Done;
import akka.NotUsed;
import akka.stream.javadsl.Flow;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

public class CharlieDownstreamServiceImpl implements CharlieDownstreamService {

    private final ConcurrentLinkedQueue<ReceivedMessage> receivedMessages = new ConcurrentLinkedQueue<>();
    private final BetaUpstreamService betaUpstreamService;

    @Inject
    public CharlieDownstreamServiceImpl(
            AlphaUpstreamService alphaUpstreamService,
            BetaUpstreamService betaUpstreamService) {
        this.betaUpstreamService = betaUpstreamService;
        alphaUpstreamService
                .messageTopic()
                .subscribe()
                .withGroupId("downstream-charlie")
                .atLeastOnce(
                        // append messages into receivedMessages
                        Flow.<AlphaEvent>create().map(alphaEvent -> {
                            receivedMessages.add(new ReceivedMessage("A", alphaEvent.getCode()));
                            return Done.getInstance();
                        })
                );
    }

    @Override
    public ServiceCall<NotUsed, Done> startSubscriptionOnBeta() {
        betaUpstreamService
                .messageTopic()
                .subscribe()
                .atLeastOnce(
                        // append messages into receivedMessages
                        Flow.<BetaEvent>create().map(betaEvent -> {
                            receivedMessages.add(new ReceivedMessage("B", betaEvent.getCode()));
                            return Done.getInstance();
                        })
                );
        return req -> CompletableFuture.completedFuture(Done.getInstance());
    }

    @Override
    public ServiceCall<NotUsed, PSequence<ReceivedMessage>> retrieveMessagesC() {
        return req -> CompletableFuture.completedFuture(TreePVector.from(receivedMessages));
    }
}
