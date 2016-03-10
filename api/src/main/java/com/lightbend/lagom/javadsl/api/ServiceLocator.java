/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Locates services.
 */
public interface ServiceLocator {

    /**
     * Locate a service with the given name.
     *
     * @param name The name of the service.
     * @return The URI for that service, if it exists.
     */
    CompletionStage<Optional<URI>> locate(String name);

    /**
     * Do the given action with the given service.
     *
     * This should be used in preference to {@link #locate(String)} when possible as it will allow the service
     * locator update its caches in case there's a problem with the service it returned.
     *
     * @param name The name of the service.
     * @param block A block of code that takes the URI for the service, and returns a future of some work done on the
     *              service. This will only be executed if the service was found.
     * @return The result of the executed block, if the service was found.
     */
    <T> CompletionStage<Optional<T>> doWithService(String name, Function<URI, CompletionStage<T>> block);
}
