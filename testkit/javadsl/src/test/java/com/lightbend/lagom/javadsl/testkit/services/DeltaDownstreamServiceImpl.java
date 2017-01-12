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

public class DeltaDownstreamServiceImpl implements DeltaDownstreamService {

    private volatile PSequence<ReceivedMessage> receivedMessages = TreePVector.empty();

    @Inject
    public DeltaDownstreamServiceImpl(AlphaUpstreamService alphaUpstreamService) {
        alphaUpstreamService
                .messageTopic()
                .subscribe()
                .withGroupId("downstream-delta")
                .atLeastOnce(
                        // append messages into receivedMessages
                        Flow.<AlphaEvent>create().map(alphaEvent -> {
                            receivedMessages = receivedMessages.plus(new ReceivedMessage("A", alphaEvent.getCode()));
                            return Done.getInstance();
                        })
                );
    }

    @Override
    public ServiceCall<NotUsed, PSequence<ReceivedMessage>> retrieveMessagesD() {
        return req -> CompletableFuture.completedFuture(receivedMessages);
    }
}
