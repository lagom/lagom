/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit.services;

import akka.NotUsed;
import akka.stream.Materializer;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.namedCall;


public interface FoxtrotDownstreamService extends Service {

    ServiceCall<NotUsed, PSequence<ReceivedMessage>> retrieveMessagesF();

    default Descriptor descriptor() {
        return named("foxtrot")
                .withCalls(
                        namedCall("retrieveMessagesF", this::retrieveMessagesF)
                );
    }

    class Impl implements FoxtrotDownstreamService {

        private volatile PSequence<ReceivedMessage> receivedMessages = TreePVector.empty();

        @Inject
        public Impl(AlphaUpstreamService alphaUpstreamService, Materializer materializer) {
            alphaUpstreamService
                    .messageTopic()
                    .subscribe()
                    .withGroupId("downstream-foxtrot")
                    .atMostOnceSource()
                    .runForeach(
                            alphaEvent -> receivedMessages = receivedMessages.plus(new ReceivedMessage("A", alphaEvent.getCode())),
                            materializer
                    );
        }

        @Override
        public ServiceCall<NotUsed, PSequence<ReceivedMessage>> retrieveMessagesF() {
            return req -> CompletableFuture.completedFuture(receivedMessages);
        }
    }
}
