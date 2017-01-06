/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.server;

import akka.japi.Pair;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.RequestHeader;
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader;

import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A server implementation of a service call.
 *
 * While the server implementation of the service doesn't have to make use of this type, what this type does is it
 * allows the supply and composition of request and response headers.  When working with and or composing server
 * service calls, it is almost never a good idea to call {@link #invoke(Object)}, rather,
 * {@link #invokeWithHeaders(RequestHeader, Object)} should be called. Invocation of the former may result in
 * an {@link UnsupportedOperationException} being thrown.
 *
 * In some cases, where the underlying transport doesn't allow sending a header after the request message has been
 * received (eg WebSockets), the response header may be ignored. In these cases, Lagom will make a best effort attempt
 * at determining whether there was custom information in the response header, and if so, log a warning that it wasn't
 * set.
 *
 * As this is a functional interface, it is generally advised that you implement it using a lambda.
 *
 * If you want to actually handle the headers in a service call, it is recommended that rather than implementing this
 * interface, you use {@link HeaderServiceCall}, which makes the
 * {@link #invokeWithHeaders(RequestHeader, Object)} method abstract so that it can be implemented with a
 * lambda.
 */
@FunctionalInterface
public interface ServerServiceCall<Request, Response> extends ServiceCall<Request, Response> {

    /**
     * Invoke the given action with the request and response headers.
     *
     * @param requestHeader The request header.
     * @param request The request message.
     * @return A future of the response header and response message.
     */
    default CompletionStage<Pair<ResponseHeader, Response>> invokeWithHeaders(RequestHeader requestHeader, Request request) {
        return invoke(request).thenApply(response -> Pair.create(ResponseHeader.OK, response));
    }

    @Override
    default <T> ServerServiceCall<Request, T> handleResponseHeader(BiFunction<ResponseHeader, Response, T> handler) {
        ServerServiceCall<Request, Response> self = this;
        return new ServerServiceCall<Request, T>() {
            @Override
            public CompletionStage<Pair<ResponseHeader, T>> invokeWithHeaders(RequestHeader requestHeader, Request request) {
                return self.invokeWithHeaders(requestHeader, request).thenApply(pair -> Pair.create(pair.first(),
                        handler.apply(pair.first(), pair.second())));
            }

            @Override
            public CompletionStage<T> invoke(Request request) {
                // Typically, the transport will attach a response header handler after it attaches a request header
                // handler.  So this service call will be the one that it invokes, meaning this is method that it
                // will call, and self will be the service call returned by handleRequestHeader.
                return self.invokeWithHeaders(RequestHeader.DEFAULT, request)
                        .thenApply(pair -> handler.apply(pair.first(), pair.second()));
            }
        };
    }

    @Override
    default ServerServiceCall<Request, Response> handleRequestHeader(Function<RequestHeader, RequestHeader> handler) {
        ServerServiceCall<Request, Response> self = this;
        return new ServerServiceCall<Request, Response>() {
            @Override
            public CompletionStage<Pair<ResponseHeader, Response>> invokeWithHeaders(RequestHeader requestHeader, Request request) {
                // Typically, this will be invoked by the service call returned by handleResponseHeader, which will
                // appropriately handle the response header returned by invokeWithHeaders.  Self will typically be the
                // user supplied service call.
                return self.invokeWithHeaders(handler.apply(requestHeader), request);
            }
            @Override
            public CompletionStage<Response> invoke(Request request) {
                RequestHeader requestHeader = handler.apply(RequestHeader.DEFAULT);
                return self.invokeWithHeaders(requestHeader, request).thenApply(Pair::second);
            }
        };
    }
}
