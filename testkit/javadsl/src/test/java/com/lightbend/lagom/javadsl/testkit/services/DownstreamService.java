/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit.services;

import akka.Done;
import akka.NotUsed;
import akka.stream.Materializer;
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


public interface DownstreamService extends Service {

    ServiceCall<NotUsed, Done> startSubscriptionOnBeta();

    ServiceCall<NotUsed, PSequence<ReceivedMessage>> retrieveMessagesC();

    ServiceCall<NotUsed, PSequence<ReceivedMessage>> retrieveMessagesD();

    ServiceCall<NotUsed, PSequence<ReceivedMessage>> retrieveMessagesF();


    default Descriptor descriptor() {
        return named("charlie")
                .withCalls(
                        namedCall("startSubscriptionOnBeta", this::startSubscriptionOnBeta),
                        namedCall("retrieveMessagesC", this::retrieveMessagesC),
                        namedCall("retrieveMessagesD", this::retrieveMessagesD),
                        namedCall("retrieveMessagesF", this::retrieveMessagesF)
                );
    }

    class Impl implements DownstreamService {

        private final ConcurrentLinkedQueue<ReceivedMessage> receivedMessagesC = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<ReceivedMessage> receivedMessagesF = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<ReceivedMessage> receivedMessagesD = new ConcurrentLinkedQueue<>();
        private final BetaUpstreamService betaUpstreamService;


        @Inject
        public Impl(AlphaUpstreamService alphaUpstreamService, BetaUpstreamService betaUpstreamService, Materializer materializer) {
            this.betaUpstreamService = betaUpstreamService;
            alphaUpstreamService
                    .messageTopic()
                    .subscribe()
                    .withGroupId("downstream-charlie")
                    .atLeastOnce( // !! at least once. 'receivedMessagesC' is a shared buffer.
                            Flow.<AlphaEvent>create().map(alphaEvent -> {
                                receivedMessagesC.add(new ReceivedMessage("A", alphaEvent.getCode()));
                                return Done.getInstance();
                            })
                    );

            alphaUpstreamService
                    .messageTopic()
                    .subscribe()
                    .withGroupId("downstream-delta")
                    .atLeastOnce( // !! at least once
                            Flow.<AlphaEvent>create().map(alphaEvent -> {
                                receivedMessagesD.add(new ReceivedMessage("A", alphaEvent.getCode()));
                                return Done.getInstance();
                            })
                    );

            alphaUpstreamService
                    .messageTopic()
                    .subscribe()
                    .withGroupId("downstream-foxtrot")
                    .atMostOnceSource() // !! at most once
                    .runForeach(
                            alphaEvent -> receivedMessagesF.add(new ReceivedMessage("A", alphaEvent.getCode())),
                            materializer
                    );
        }


        @Override
        public ServiceCall<NotUsed, Done> startSubscriptionOnBeta() {
            betaUpstreamService
                    .messageTopic()
                    .subscribe()
                    .atLeastOnce( // !! at least once. 'receivedMessagesC' is a shared buffer.
                            Flow.<BetaEvent>create().map(betaEvent -> {
                                receivedMessagesC.add(new ReceivedMessage("B", betaEvent.getCode()));
                                return Done.getInstance();
                            })
                    );
            return req -> CompletableFuture.completedFuture(Done.getInstance());
        }

        @Override
        public ServiceCall<NotUsed, PSequence<ReceivedMessage>> retrieveMessagesC() {
            return req -> CompletableFuture.completedFuture(TreePVector.from(receivedMessagesC));
        }


        @Override
        public ServiceCall<NotUsed, PSequence<ReceivedMessage>> retrieveMessagesD() {
            return req -> CompletableFuture.completedFuture(TreePVector.from(receivedMessagesD));
        }

        @Override
        public ServiceCall<NotUsed, PSequence<ReceivedMessage>> retrieveMessagesF() {
            return req -> CompletableFuture.completedFuture(TreePVector.from(receivedMessagesF));
        }
    }

}
