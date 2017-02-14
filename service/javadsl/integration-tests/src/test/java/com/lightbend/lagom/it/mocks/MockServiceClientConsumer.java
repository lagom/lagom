/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it.mocks;

import akka.japi.Pair;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader;

import javax.inject.Inject;
import java.util.Collection;
import java.util.concurrent.CompletionStage;

public class MockServiceClientConsumer {
    private final MockService client;

    @Inject
    public MockServiceClientConsumer(MockService client) {
        this.client = client;
    }

    public CompletionStage<Pair<ResponseHeader, String>> invokeCustomHeaders(String headerName, String headerValue) {
        return client.customHeaders()
                .handleRequestHeader(rh -> rh.withHeader(headerName, headerValue))
                .withResponseHeader()
                .invoke(headerName);
    }

    public CompletionStage<Source<String, ?>> invokeStreamCustomHeaders(Collection<Pair<String, String>> headers) {
        return client.streamCustomHeaders()
                .handleRequestHeader(rh -> {
                    for (Pair<String, String> header: headers) {
                        rh = rh.withHeader(header.first(), header.second());
                    }
                    return rh;
                })
                .invoke(Source.from(headers).map(Pair::first).concat(Source.maybe()));
    }

}
