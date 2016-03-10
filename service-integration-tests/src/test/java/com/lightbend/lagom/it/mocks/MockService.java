/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.it.mocks;

import com.lightbend.lagom.javadsl.api.Descriptor.CircuitBreakerId;

import akka.Done;
import akka.stream.javadsl.Source;
import akka.NotUsed;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.deser.IdSerializers;
import com.lightbend.lagom.javadsl.api.transport.Method;
import static com.lightbend.lagom.javadsl.api.Service.*;
import java.util.Arrays;

public interface MockService extends Service {

    ServiceCall<MockId, MockRequestEntity, MockResponseEntity> mockCall();

    ServiceCall<NotUsed, NotUsed, NotUsed> doNothing();
    
    ServiceCall<NotUsed, NotUsed, NotUsed> alwaysFail();
    
    ServiceCall<NotUsed, Done, Done> doneCall();

    ServiceCall<NotUsed, MockRequestEntity, Source<MockResponseEntity, ?>> streamResponse();

    ServiceCall<NotUsed, NotUsed, Source<MockResponseEntity, ?>> unitStreamResponse();

    ServiceCall<NotUsed, Source<MockRequestEntity, ?>, MockResponseEntity> streamRequest();

    ServiceCall<NotUsed, Source<MockRequestEntity, ?>, NotUsed> streamRequestUnit();

    ServiceCall<NotUsed, Source<MockRequestEntity, ?>, Source<MockResponseEntity, ?>> bidiStream();

    ServiceCall<NotUsed, String, String> customHeaders();

    ServiceCall<NotUsed, Source<String, ?>, Source<String, ?>> streamCustomHeaders();

    ServiceCall<NotUsed, NotUsed, String> serviceName();

    ServiceCall<NotUsed, NotUsed, Source<String, ?>> streamServiceName();

    default Descriptor descriptor() {
        return named("mockservice").with(
                restCall(Method.POST, "/:part1/:part2", mockCall())
                        .with(IdSerializers.create("mockId", MockId::new, id -> Arrays.asList(id.part1(), id.part2()))),
                call(doNothing()),
                call(alwaysFail()).withCircuitBreaker(new CircuitBreakerId("foo")),
                call(doneCall()),
                call(streamResponse()),
                call(unitStreamResponse()),
                call(streamRequest()),
                call(streamRequestUnit()),
                call(bidiStream()),
                call(customHeaders()),
                call(streamCustomHeaders()),
                call(serviceName()),
                call(streamServiceName())
        );
    }
}
