/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.it.mocks;

import akka.Done;
import akka.japi.Pair;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.NotUsed;

import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.NotFound;
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader;
import com.lightbend.lagom.javadsl.server.HeaderServiceCall;
import com.lightbend.lagom.javadsl.server.ServerServiceCall;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MockServiceImpl implements MockService {

    private final Materializer materializer;
  
    @Inject
    public MockServiceImpl(Materializer materializer) {
      this.materializer = materializer;
    }

    @Override
    public ServiceCall<MockRequestEntity, MockResponseEntity> mockCall(long id) {
        return request -> CompletableFuture.completedFuture(new MockResponseEntity(id, request));
    }

    @Override
    public ServiceCall<NotUsed, NotUsed> doNothing() {
        return request -> {
            invoked.set(true);
            return CompletableFuture.completedFuture(NotUsed.getInstance());
        };
    }
    
    @Override
    public ServiceCall<NotUsed, NotUsed> alwaysFail() {
        return request -> {
            invoked.set(true);
            throw new RuntimeException("Simulated error");
        };
    }
    
    @Override
    public ServiceCall<Done, Done> doneCall(){
      return done -> CompletableFuture.completedFuture(done);
  }

    public static final AtomicBoolean invoked = new AtomicBoolean();

    @Override
    public ServiceCall<MockRequestEntity, Source<MockResponseEntity, ?>> streamResponse() {
        return request ->
            CompletableFuture.completedFuture(Source.from(Arrays.asList(1, 2, 3)).map(i ->
                new MockResponseEntity(i, request)
            ));
    }

    @Override
    public ServiceCall<NotUsed, Source<MockResponseEntity, ?>> unitStreamResponse() {
        return request -> {
                System.out.println("unit stream response invoked");
                return CompletableFuture.completedFuture(Source.from(Arrays.asList(1, 2, 3)).map(i ->
                        new MockResponseEntity(i, new MockRequestEntity("entity", i))
                ));};
    }

    @Override
    public ServiceCall<Source<MockRequestEntity, ?>, MockResponseEntity> streamRequest() {
        return request ->
                request.runWith(Sink.head(), materializer)
                        .thenApply(head -> new MockResponseEntity(1, head));
    }

    public static AtomicReference<MockRequestEntity> firstReceived = new AtomicReference<>();

    @Override
    public ServiceCall<Source<MockRequestEntity, ?>, NotUsed> streamRequestUnit() {
        return request ->
            request.runWith(Sink.head(), materializer)
                    .thenApply(head -> {
                        firstReceived.set(head);
                        return NotUsed.getInstance();
                    });
    }

    @Override
    public ServiceCall<Source<MockRequestEntity, ?>, Source<MockResponseEntity, ?>> bidiStream() {
        return request -> CompletableFuture.completedFuture(
                request.map(req -> new MockResponseEntity(1, req))
        );
    }

    @Override
    public HeaderServiceCall<String, String> customHeaders() {
        return (requestHeader, headerName) -> {
            String headerValue = requestHeader.getHeader(headerName).orElseGet(() -> {
                throw new NotFound("Header " + headerName);
            });
            return CompletableFuture.completedFuture(
                    Pair.create(ResponseHeader.OK.withStatus(201).withHeader("Header-Name", headerName),
                    headerValue));
        };
    }

    @Override
    public HeaderServiceCall<Source<String, ?>, Source<String, ?>> streamCustomHeaders() {
        return (requestHeader, headerNames) ->
            CompletableFuture.completedFuture(Pair.create(ResponseHeader.OK, headerNames.map(headerName ->
                requestHeader.getHeader(headerName).orElseGet(() -> {
                    throw new NotFound("Header " + headerName);
                }
            ))));
    }

    @Override
    public ServiceCall<NotUsed, String> serviceName() {
        return withServiceName(serviceName -> request ->
                CompletableFuture.completedFuture(serviceName)
        );
    }

    @Override
    public ServiceCall<NotUsed, Source<String, ?>> streamServiceName() {
        return withServiceName(serviceName -> request ->
                CompletableFuture.completedFuture(Source.single(serviceName))
        );
    }

    @Override
    public ServiceCall<NotUsed, String> queryParamId(Optional<String> query) {
        return request -> CompletableFuture.completedFuture(query.orElse("none"));
    }

    @Override
    public ServiceCall<MockRequestEntity, List<MockResponseEntity>> listResults() {
        return request -> CompletableFuture.completedFuture(
                IntStream.range(0, request.field2())
                        .mapToObj(i -> new MockResponseEntity(i, request))
                        .collect(Collectors.toList())
        );
    }

    @Override
    public ServiceCall<MockRequestEntity, MockResponseEntity> customContentType() {
        return request -> CompletableFuture.completedFuture(new MockResponseEntity(request.field2(), request));
    }

    @Override
    public ServiceCall<MockRequestEntity, MockResponseEntity> noContentType() {
        return request -> CompletableFuture.completedFuture(new MockResponseEntity(request.field2(), request));
    }

    /**
     * Shows example service call composition.
     */
    private <Request, Response> ServerServiceCall<Request, Response> withServiceName(
            Function<String, ServerServiceCall<Request, Response>> block) {

        return HeaderServiceCall.compose(requestHeader -> {

            // Get the service name
            String serviceName = requestHeader.principal().orElseGet(() -> {
                throw new NotFound("Server principal");
            }).getName();

            // Pass it to the block, and
            return block.apply(serviceName);
        });
    }


}
