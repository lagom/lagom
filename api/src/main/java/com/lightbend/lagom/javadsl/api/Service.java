/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api;

import java.util.Optional;

import com.lightbend.lagom.internal.api.UnresolvedMessageTypeSerializer;
import com.lightbend.lagom.internal.api.UnresolvedTypeIdSerializer;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.transport.Method;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A self describing service.
 *
 * This interface implements a DSL for describing a service. It is inherently constrained in its use.
 *
 * A service can describe itself by defining an interface that extends this interface, and provides a default
 * implementation for the {@link #descriptor()} method.
 */
public interface Service {

    /**
     * Describe this service.
     *
     * The intended mechanism for implementing this is to provide it as a default implementation on an interface.
     */
    Descriptor descriptor();

    /**
     * Create a descriptor for a service with the given name.
     *
     * @param name The name of the service.
     * @return The descriptor.
     */
    static Descriptor named(String name) {
        return new Descriptor(name);
    }

    /**
     * Create a REST service call descriptor, identified by the given HTTP method and path pattern.
     *
     * @param method The HTTP method.
     * @param pathPattern The path pattern.
     * @param serviceCall The service call.
     * @return A REST service call descriptor.
     */
    static <Id, Request, Response> Descriptor.Call<Id, Request, Response> restCall(Method method,
            String pathPattern, ServiceCall<Id, Request, Response> serviceCall) {
        return call(new Descriptor.RestCallId(method, pathPattern), serviceCall);
    }

    /**
     * Create a service call descriptor, identified by the given path pattern.
     *
     * @param pathPattern The path pattern.
     * @param serviceCall The service call.
     * @return A path identified service call descriptor.
     */
    static <Id, Request, Response> Descriptor.Call<Id, Request, Response> pathCall(String pathPattern,
            ServiceCall<Id, Request, Response> serviceCall) {
        return call(new Descriptor.PathCallId(pathPattern), serviceCall);
    }

    /**
     * Create a service call descriptor, identified by the given name.
     *
     * @param name The name of the service call.
     * @param serviceCall The service call.
     * @return A name identified service call descriptor.
     */
    static <Request, Response> Descriptor.Call<NotUsed, Request, Response> namedCall(String name,
            ServiceCall<NotUsed, Request, Response> serviceCall) {
        return call(new Descriptor.NamedCallId(name), serviceCall);
    }


    /**
     * Create a service call descriptor.
     *
     * The identifier for this descriptor will be automatically selected by Lagom.
     *
     * @param serviceCall The service call.
     * @return A name identified service call descriptor.
     */
    static <Request, Response> Descriptor.Call<NotUsed, Request, Response> call(
            ServiceCall<NotUsed, Request, Response> serviceCall) {
        if (serviceCall instanceof SelfDescribingServiceCall) {
            SelfDescribingServiceCall<NotUsed, Request, Response> describing = (SelfDescribingServiceCall<NotUsed, Request, Response>) serviceCall;
            return call(new Descriptor.NamedCallId(describing.methodName()), serviceCall);
        } else {
            throw new IllegalArgumentException("Passed in service call is not self describing. This typically means " +
                    "either the Service.endpoint() method has been invoked outside of the " +
                    "Service.descriptor() method, or the Service.descriptor() method has " +
                    "been invoked manually.");
        }
    }

    /**
     * Create a service call descriptor.
     *
     * @param callId The service call identifier.
     * @param serviceCall The service call.
     * @return A service call descriptor.
     */
    static <Id, Request, Response> Descriptor.Call<Id, Request, Response> call(Descriptor.CallId callId,
            ServiceCall<Id, Request, Response> serviceCall) {
        if (serviceCall instanceof SelfDescribingServiceCall) {
            SelfDescribingServiceCall<Id, Request, Response> describing = (SelfDescribingServiceCall<Id, Request, Response>) serviceCall;
            return new Descriptor.Call<>(callId, serviceCall, new UnresolvedTypeIdSerializer<>(describing.idType()),
                    new UnresolvedMessageTypeSerializer<>(describing.requestType()),
                    new UnresolvedMessageTypeSerializer<>(describing.responseType()), Optional.empty(), Optional.empty());
        } else {
            throw new IllegalArgumentException("Passed in service call is not self describing. This typically means " +
                    "either the Service.endpoint() method has been invoked outside of the " +
                    "Service.descriptor() method, or the Service.descriptor() method has " +
                    "been invoked manually.");
        }
    }

    /**
     * A self describing service call.
     *
     * Self describing service calls return generic type information that is otherwise lost due to type erasure. They
     * are typically implemented by the Lagom framework, which inspects the return types of service calls.
     */
    interface SelfDescribingServiceCall<Id, Request, Response> extends ServiceCall<Id, Request, Response> {
        /**
         * Get the type of the ID.
         *
         * @return The type of the ID.
         */
        Type idType();

        /**
         * Get the type of the request.
         *
         * @return The type of the request.
         */
        Type requestType();

        /**
         * Get the type of the response.
         *
         * @return The type of the response.
         */
        Type responseType();

        /**
         * Get the name of the method that defines the call.
         *
         * @return The name of the method.
         */
        String methodName();
    }

}
