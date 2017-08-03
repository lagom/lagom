/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Locates services.
 *
 * The service locator is responsible for two things, one is locating services according to the passed in name and
 * service call information, the other is to implement circuit breaking functionality when
 * {@link #doWithService(String, Descriptor.Call, Function)} is invoked.
 *
 * The reason circuit breaking is a service locator concern is that generally, the service locator will want to be aware
 * of when a circuit breaker is open, and respond accordingly.  For example, it may decide to pull that node from its
 * routing pool, or it may decide to notify some up stream service registry that that node is no longer responding.
 */
public interface ServiceLocator {

    /**
     * Locate a service's URI for the given name.
     *
     * @param name The name of the service.
     * @return The URI for that service, if it exists.
     */
    default CompletionStage<Optional<URI>> locate(String name) {
        return locate(name, Descriptor.Call.NONE);
    }

    /**
     * Locate the service's URIs for the given name.
     *
     * @param name The name of the service.
     * @return One or more URIs for that service, otherwise an empty List if none is found.
     * @since 1.4
     */
    default CompletionStage<List<URI>> locateAll(String name) {
        return locateAll(name, Descriptor.Call.NONE);
    }


    /**
     * Locate a service's URI for the given name.
     *
     * @param name The name of the service.
     * @param serviceCall The service call descriptor that this lookup is for.
     * @return The URI for that service, if it exists.
     */
    CompletionStage<Optional<URI>> locate(String name, Descriptor.Call<?, ?> serviceCall);


    /**
     * Locate the service's URIs for the given name.
     *
     * @param name The name of the service.
     * @param serviceCall The service call descriptor that this lookup is for.
     * @return One or more URIs for that service, otherwise an empty List if none is found.
     * @since 1.4
     */
    default CompletionStage<List<URI>> locateAll(String name, Descriptor.Call<?, ?> serviceCall) {
        return locate(name, serviceCall)
                .thenApply( opt -> opt.map(Collections::singletonList).orElseGet(Collections::emptyList)
        );
    }


    /**
     * Do the given action with the given service.
     *
     * This should be used in preference to {@link #locate(String, Descriptor.Call)} when possible as it will allow the
     * service locator to add in things like circuit breakers.
     *
     * It is required that the service locator will, based on the service call circuit breaker configuration, wrap the
     * invocation of the passed in block with a circuit breaker.
     *
     * @param name The name of the service.
     * @param serviceCall The service call descriptor that this lookup is for.
     * @param block A block of code that takes the URI for the service, and returns a future of some work done on the
     *              service. This will only be executed if the service was found.
     * @return The result of the executed block, if the service was found.
     */
    <T> CompletionStage<Optional<T>> doWithService(String name,
                                                   Descriptor.Call<?, ?> serviceCall,
                                                   Function<URI, CompletionStage<T>> block);
}
