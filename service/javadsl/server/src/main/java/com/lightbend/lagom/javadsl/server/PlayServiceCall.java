/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.server;

import com.lightbend.lagom.javadsl.api.ServiceCall;
import play.mvc.EssentialAction;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * A service call implementation that allows plugging directly into Play's request handling.
 */
@FunctionalInterface
public interface PlayServiceCall<Request, Response> extends ServiceCall<Request, Response> {

    @Override
    default CompletionStage<Response> invoke(Request request) {
        throw new UnsupportedOperationException("Play service call must be invoked using Play specific methods");
    }

    /**
     * Low level hook for implementing service calls directly in Play.
     *
     * This can only be used to hook into plain HTTP calls, it can't be used to hook into WebSocket calls.
     *
     * @param wrapCall A function that takes a service call, and converts it to an EssentialAction.  This action can
     *                 then be composed to modify any part of the request and or response, including request and
     *                 response headers and the request and response body.  This does not have to be invoked at all if
     *                 it's not desired, but may be useful to get the benefit of all the Lagom features such as
     *                 serialization and deserialization.
     * @return An EssentialAction to handle the call with.
     */
    EssentialAction invoke(Function<ServiceCall<Request, Response>, EssentialAction> wrapCall);
}
