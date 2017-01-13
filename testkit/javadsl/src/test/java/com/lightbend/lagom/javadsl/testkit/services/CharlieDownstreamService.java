/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit.services;

import akka.Done;
import akka.NotUsed;
import akka.stream.javadsl.Flow;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.namedCall;


public interface CharlieDownstreamService extends Service {

    ServiceCall<NotUsed, Done> startSubscriptionOnBeta();

    ServiceCall<NotUsed, PSequence<ReceivedMessage>> retrieveMessagesC();

    default Descriptor descriptor() {
        return named("charlie")
                .withCalls(
                        namedCall("startSubscriptionOnBeta", this::startSubscriptionOnBeta),
                        namedCall("retrieveMessagesC", this::retrieveMessagesC)
                );
    }

    class Impl implements CharlieDownstreamService {

        private final ConcurrentLinkedQueue<ReceivedMessage> receivedMessages = new ConcurrentLinkedQueue<>();
        private final BetaUpstreamService betaUpstreamService;

        @Inject
        public Impl(AlphaUpstreamService alphaUpstreamService, BetaUpstreamService betaUpstreamService) {
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

}
