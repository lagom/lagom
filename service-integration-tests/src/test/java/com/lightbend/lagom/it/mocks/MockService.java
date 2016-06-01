/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.it.mocks;

import com.lightbend.lagom.javadsl.api.CircuitBreaker;

import akka.Done;
import akka.stream.javadsl.Source;
import akka.NotUsed;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import static com.lightbend.lagom.javadsl.api.Service.*;

import java.util.Optional;

public interface MockService extends Service {

    ServiceCall<MockRequestEntity, MockResponseEntity> mockCall(long id);

    ServiceCall<NotUsed, NotUsed> doNothing();
    
    ServiceCall<NotUsed, NotUsed> alwaysFail();
    
    ServiceCall<Done, Done> doneCall();

    ServiceCall<MockRequestEntity, Source<MockResponseEntity, ?>> streamResponse();

    ServiceCall<NotUsed, Source<MockResponseEntity, ?>> unitStreamResponse();

    ServiceCall<Source<MockRequestEntity, ?>, MockResponseEntity> streamRequest();

    ServiceCall<Source<MockRequestEntity, ?>, NotUsed> streamRequestUnit();

    ServiceCall<Source<MockRequestEntity, ?>, Source<MockResponseEntity, ?>> bidiStream();

    ServiceCall<String, String> customHeaders();

    ServiceCall<Source<String, ?>, Source<String, ?>> streamCustomHeaders();

    ServiceCall<NotUsed, String> serviceName();

    ServiceCall<NotUsed, Source<String, ?>> streamServiceName();

    ServiceCall<NotUsed, String> queryParamId(Optional<String> query);

    default Descriptor descriptor() {
        return named("mockservice").withCalls(
                restCall(Method.POST, "/mock/:id", this::mockCall),
                call(this::doNothing),
                call(this::alwaysFail).withCircuitBreaker(CircuitBreaker.identifiedBy("foo")),
                call(this::doneCall),
                call(this::streamResponse),
                call(this::unitStreamResponse),
                call(this::streamRequest),
                call(this::streamRequestUnit),
                call(this::bidiStream),
                call(this::customHeaders),
                call(this::streamCustomHeaders),
                call(this::serviceName),
                call(this::streamServiceName),
                pathCall("/queryparam?qp", this::queryParamId)
        );
    }
}
