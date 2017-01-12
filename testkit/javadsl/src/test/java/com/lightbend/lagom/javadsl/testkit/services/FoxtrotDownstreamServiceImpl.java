/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit.services;

import akka.NotUsed;
import akka.stream.Materializer;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

public class FoxtrotDownstreamServiceImpl implements FoxtrotDownstreamService {

    private volatile PSequence<ReceivedMessage> receivedMessages = TreePVector.empty();

    @Inject
    public FoxtrotDownstreamServiceImpl(AlphaUpstreamService alphaUpstreamService, Materializer materializer) {
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
