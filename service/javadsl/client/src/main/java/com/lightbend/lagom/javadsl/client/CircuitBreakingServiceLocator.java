/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.client;

import com.lightbend.lagom.internal.client.CircuitBreakers;
import com.lightbend.lagom.internal.javadsl.client.CircuitBreakersPanelImpl;
import com.lightbend.lagom.javadsl.api.CircuitBreaker;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.ServiceLocator;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Abstract service locator that provides circuit breaking.
 *
 * Generally, only the {@link #locate(String, Descriptor.Call)} method needs to be implemented, however
 * {@link #doWithServiceImpl(String, Descriptor.Call, Function)} can be overridden if the service locator wants to
 * handle failures in some way.
 */
public abstract class CircuitBreakingServiceLocator implements ServiceLocator {

    private final CircuitBreakersPanel circuitBreakersPanel;

    public CircuitBreakingServiceLocator(CircuitBreakersPanel circuitBreakersPanel) {
        this.circuitBreakersPanel = circuitBreakersPanel;
    }

    /**
     * @deprecated Use constructor accepting {@link CircuitBreakersPanel} instead
     * @param circuitBreakers
     */
    @Deprecated
    public CircuitBreakingServiceLocator(CircuitBreakers circuitBreakers) {
        // convert passed Scala CircuitBreaker to a Java one
        this.circuitBreakersPanel = new CircuitBreakersPanelImpl(circuitBreakers);
    }

    /**
     * Do the given block with the given service looked up.
     *
     * This is invoked by {@link #doWithService(String, Descriptor.Call, Function)}, after wrapping the passed in block
     * in a circuit breaker if configured to do so.
     *
     * The default implementation just delegates to the {@link #locate(String, Descriptor.Call)} method, but this method
     * can be overridden if the service locator wants to inject other behaviour after the service call is complete.
     *
     * @param name The service name.
     * @param serviceCall The service call that needs the service lookup.
     * @param block A block of code that will use the looked up service, typically, to make a call on that service.
     * @return A future of the result of the block, if the service lookup was successful.
     */
    protected <T> CompletionStage<Optional<T>> doWithServiceImpl(String name, Descriptor.Call<?, ?> serviceCall, Function<URI, CompletionStage<T>> block) {
        return locate(name, serviceCall).thenCompose(uri -> {
          return uri
                  .map(u -> block.apply(u).thenApply(Optional::of))
                  .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()));
        });
    }

    @Override
    public final <T> CompletionStage<Optional<T>> doWithService(String serviceName, Descriptor.Call<?, ?> serviceCall, Function<URI, CompletionStage<T>> block) {

        return serviceCall.circuitBreaker()
                .filter(cb -> !cb.equals(CircuitBreaker.none()))
                .map(cb -> {

                    String circuitBreakerId;

                    if (cb instanceof CircuitBreaker.CircuitBreakerId) {
                        circuitBreakerId = ((CircuitBreaker.CircuitBreakerId) cb).id();
                    } else {
                        circuitBreakerId = serviceName;
                    }

                    return doWithServiceImpl(
                            serviceName,
                            serviceCall,
                            uri -> circuitBreakersPanel.withCircuitBreaker(circuitBreakerId, () -> block.apply(uri))
                          );

                })
                .orElseGet(
                  () -> doWithServiceImpl(serviceName, serviceCall, block)
                );
    }
}
