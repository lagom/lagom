/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class MockServiceImpl implements MockService {

    private final Materializer materializer;
  
    @Inject
    public MockServiceImpl(Materializer materializer) {
      this.materializer = materializer;
    }

    @Override
    public ServiceCall<MockId, MockRequestEntity, MockResponseEntity> mockCall() {
        return (id, request) -> CompletableFuture.completedFuture(new MockResponseEntity(id, request));
    }

    @Override
    public ServiceCall<NotUsed, NotUsed, NotUsed> doNothing() {
        return (id, request) -> {
            invoked.set(true);
            return CompletableFuture.completedFuture(NotUsed.getInstance());
        };
    }
    
    @Override
    public ServiceCall<NotUsed, NotUsed, NotUsed> alwaysFail() {
        return (id, request) -> {
            invoked.set(true);
            throw new RuntimeException("Simulated error");
        };
    }
    
    @Override
    public ServiceCall<NotUsed, Done, Done> doneCall(){
      return (id, done) -> CompletableFuture.completedFuture(done);
  }

    public static final AtomicBoolean invoked = new AtomicBoolean();

    @Override
    public ServiceCall<NotUsed, MockRequestEntity, Source<MockResponseEntity, ?>> streamResponse() {
        return (id, request) ->
            CompletableFuture.completedFuture(Source.from(Arrays.asList(1, 2, 3)).map(i ->
                new MockResponseEntity(new MockId("id", i), request)
            ));
    }

    @Override
    public ServiceCall<NotUsed, NotUsed, Source<MockResponseEntity, ?>> unitStreamResponse() {
        return (id, request) -> {
                System.out.println("unit stream response invoked");
                return CompletableFuture.completedFuture(Source.from(Arrays.asList(1, 2, 3)).map(i ->
                        new MockResponseEntity(new MockId("id", i), new MockRequestEntity("entity", i))
                ));};
    }

    @Override
    public ServiceCall<NotUsed, Source<MockRequestEntity, ?>, MockResponseEntity> streamRequest() {
        return (id, request) ->
                request.runWith(Sink.head(), materializer)
                        .thenApply(head -> new MockResponseEntity(new MockId("id", 1), head));
    }

    public static AtomicReference<MockRequestEntity> firstReceived = new AtomicReference<>();

    @Override
    public ServiceCall<NotUsed, Source<MockRequestEntity, ?>, NotUsed> streamRequestUnit() {
        return (id, request) ->
            request.runWith(Sink.head(), materializer)
                    .thenApply(head -> {
                        firstReceived.set(head);
                        return NotUsed.getInstance();
                    });
    }

    @Override
    public ServiceCall<NotUsed, Source<MockRequestEntity, ?>, Source<MockResponseEntity, ?>> bidiStream() {
        return (id, request) -> CompletableFuture.completedFuture(
                request.map(req -> new MockResponseEntity(new MockId("id", 1), req))
        );
    }

    @Override
    public HeaderServiceCall<NotUsed, String, String> customHeaders() {
        return (requestHeader, id, headerName) -> {
            String headerValue = requestHeader.getHeader(headerName).orElseGet(() -> {
                throw new NotFound("Header " + headerName);
            });
            return CompletableFuture.completedFuture(
                    Pair.create(ResponseHeader.OK.withStatus(201).withHeader("Header-Name", headerName),
                    headerValue));
        };
    }

    @Override
    public HeaderServiceCall<NotUsed, Source<String, ?>, Source<String, ?>> streamCustomHeaders() {
        return (requestHeader, id, headerNames) ->
            CompletableFuture.completedFuture(Pair.create(ResponseHeader.OK, headerNames.map(headerName ->
                requestHeader.getHeader(headerName).orElseGet(() -> {
                    throw new NotFound("Header " + headerName);
                }
            ))));
    }

    @Override
    public ServiceCall<NotUsed, NotUsed, String> serviceName() {
        return withServiceName(serviceName -> (id, request) ->
                CompletableFuture.completedFuture(serviceName)
        );
    }

    @Override
    public ServiceCall<NotUsed, NotUsed, Source<String, ?>> streamServiceName() {
        return withServiceName(serviceName -> (id, request) ->
                CompletableFuture.completedFuture(Source.single(serviceName))
        );
    }

    @Override
    public ServiceCall<Optional<String>, NotUsed, String> queryParamId() {
        return (id, request) -> CompletableFuture.completedFuture(id.orElse("none"));
    }

  /**
     * Shows example service call composition.
     */
    private <Id, Request, Response> ServerServiceCall<Id, Request, Response> withServiceName(
            Function<String, ServerServiceCall<Id, Request, Response>> block) {

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
