/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.server;

import com.lightbend.lagom.javadsl.api.ServiceCall;
import play.mvc.EssentialAction;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * An ID service call implementation that allows plugging directly into Play's request handling.
 */
@FunctionalInterface
public interface PlayServiceCall<Id, Request, Response> extends ServiceCall<Id, Request, Response> {

    @Override
    default CompletionStage<Response> invoke(Id id, Request request) {
        throw new UnsupportedOperationException("Play ID service call must be invoked using Play specific methods");
    }

    /**
     * Low level hook for implementing service calls directly in Play.
     *
     * This can only be used to hook into plain HTTP calls, it can't be used to hook into WebSocket calls.
     *
     * @param id The ID extracted from the URI.
     * @param wrapCall A function that takes a service call, and converts it to an EssentialAction.  This action can
     *                 then be composed to modify any part of the request and or response, including request and
     *                 response headers and the request and response body.  This does not have to be invoked at all if
     *                 it's not desired, but may be useful to get the benefit of all the Lagom features such as
     *                 serialization and deserialization.
     * @return An EssentialAction to handle the call with.
     */
    EssentialAction invoke(Id id, Function<ServiceCall<Id, Request, Response>, EssentialAction> wrapCall);
}
