/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit.services;

import akka.Done;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import org.pcollections.PSequence;

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

}
