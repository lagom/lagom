/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.server;

import akka.japi.Pair;
import com.lightbend.lagom.javadsl.api.transport.RequestHeader;
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * A service call that can handle headers.
 *
 * This exists as a convenience functional interface for implementing service calls that handle the request and response
 * headers.  It is intended for use in two ways, one is by changing the return type of the server implementation of a
 * service call to be this more specific type, allowing the service call to be implemented as a lambda.  The other way
 * is to invoke the {@link #of(HeaderServiceCall)} method.
 *
 * Generally, you shouldn't need to implement this interface with anything but a lambda.
 */
@FunctionalInterface
public interface HeaderServiceCall<Request, Response> extends ServerServiceCall<Request, Response> {

    /**
     * Invoke the given action with the request and response headers.
     *
     * This is overridden from {@link ServerServiceCall#invokeWithHeaders(RequestHeader, Object)} to allow
     * it to be made an abstract method.
     *
     * @param requestHeader The request header.
     * @param request The request message.
     * @return A future of the response header and response message.
     */
    @Override
    CompletionStage<Pair<ResponseHeader, Response>> invokeWithHeaders(RequestHeader requestHeader, Request request);

    @Override
    default CompletionStage<Response> invoke(Request request) {
        throw new UnsupportedOperationException("ServerServiceCalls should be invoked by using the invokeWithHeaders method.");
    }

    /**
     * Convenience method for providing a {@link HeaderServiceCall} when a method accepts a less specific type, eg
     * {@link ServerServiceCall}.  For example, the following method:
     *
     * <pre>
     * &lt;Request, Response&gt; ServerServiceCall&lt;Request, Response&gt; authenticated(
     *   Function&lt;User, ServerServiceCall&lt;Request, Response&gt;&gt; serviceCall
     * );
     * </pre>
     *
     * Could be invoked like this:
     *
     * <pre>
     * authenticated(user -&gt; HeaderServiceCall.of( (requestHeader, request) -&gt; {
     *     ...
     * }));
     * </pre>
     *
     * @param serviceCall The service call.
     */
    static <Request, Response> HeaderServiceCall<Request, Response> of(HeaderServiceCall<Request, Response> serviceCall) {
        return serviceCall;
    }

    /**
     * Compose a header service call.
     *
     * This is useful for implementing service call composition.  For example:
     *
     * <pre>
     * &lt;Request, Response&gt; ServerServiceCall&lt;Request, Response&gt; authenticated(
     *     Function&lt;String, ServerServiceCall&lt;Request, Response&gt;&gt; block) {
     *
     *     return HeaderServiceCall.compose(requestHeader -&gt; {
     *
     *         // Get the user
     *         String user = requestHeader.principal().orElseGet(() -&gt; {
     *             throw new NotAuthenticated("Not authenticated");
     *         }).getName();
     *
     *         // Pass it to the block, and return the resulting call
     *         return block.apply(user);
     *     });
     * }
     * </pre>
     *
     * @param block The block that will do the composition.
     * @return A header service call.
     */
    static<Request, Response> HeaderServiceCall<Request, Response> compose(Function<RequestHeader, ? extends ServerServiceCall<Request, Response>> block) {
        return (requestHeader, request) ->
                block.apply(requestHeader).invokeWithHeaders(requestHeader, request);
    }

    /**
     * Compose a header service call asynchronously.
     *
     * This is useful for implementing service call composition.  For example:
     *
     * <pre>
     * &lt;Request, Response&gt; ServerServiceCall&lt;Request, Response&gt; authenticated(
     *     Function&lt;String, ServerServiceCall&lt;Request, Response&gt;&gt; block) {
     *
     *     return HeaderServiceCall.composeAsync(requestHeader -&gt; {
     *
     *         // Get the user
     *         String userName = requestHeader.principal().orElseGet(() -&gt; {
     *             throw new NotAuthenticated("Not authenticated");
     *         }).getName();
     *
     *         // Load the user
     *         CompletionStage&lt;User&gt; userFuture = userService.getUser(userName);
     *
     *         // Pass it to the block, and return the resulting call
     *         return userFuture.thenApply(user -&gt; block.apply(user));
     *     });
     * }
     * </pre>
     *
     * @param block The block that will do the composition.
     * @return A header service call.
     */
    static<Request, Response> HeaderServiceCall<Request, Response> composeAsync(Function<RequestHeader, CompletionStage<? extends ServerServiceCall<Request, Response>>> block) {
        return (requestHeader, request) ->
                block.apply(requestHeader).thenCompose(call ->
                        call.invokeWithHeaders(requestHeader, request)
                );
    }

}
