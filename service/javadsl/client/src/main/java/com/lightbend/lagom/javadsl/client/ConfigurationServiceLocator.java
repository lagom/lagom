/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.client;

import com.lightbend.lagom.internal.client.CircuitBreakers;
import com.lightbend.lagom.internal.client.ConfigExtensions;
import com.lightbend.lagom.internal.javadsl.client.CircuitBreakersPanelImpl;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import play.Configuration;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * A service locator that uses static configuration and provides circuit breaker
 */
@Singleton
public class ConfigurationServiceLocator extends CircuitBreakingServiceLocator {

    private static final String LAGOM_SERVICES_KEY = "lagom.services";
    private final PMap<String, List<URI>> services;


    /**
     * @param circuitBreakers
     * @deprecated Use constructor accepting {@link CircuitBreakersPanel} instead
     */
    @Deprecated
    public ConfigurationServiceLocator(Configuration configuration, CircuitBreakers circuitBreakers) {
        this(configuration.underlying(), new CircuitBreakersPanelImpl(circuitBreakers));
    }

    @Inject
    public ConfigurationServiceLocator(Config config, CircuitBreakersPanel circuitBreakersPanel) {
        super(circuitBreakersPanel);

        Map<String, List<URI>> services = new HashMap<>();

        if (config.hasPath(LAGOM_SERVICES_KEY)) {
            Config configServices = config.getConfig(LAGOM_SERVICES_KEY);

            for (String key : configServices.root().keySet()) {
                try {

                    List<URI> uris =
                        ConfigExtensions.getStringList(configServices, key)
                            .stream()
                            .map(URI::create)
                            .collect(Collectors.toList());

                    services.put(key, uris);

                } catch (ConfigException.WrongType e) {
                    throw new IllegalStateException(
                        "Error loading configuration for " + getClass().getSimpleName() + ". " +
                            "Expected lagom.services." + key + " to be a String or a List of Strings, but was " + configServices.getValue(key).valueType(), e);
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException(
                        "Error loading configuration for  " + getClass().getSimpleName() + ". " +
                            "Expected lagom.services." + key + " to be a URI, but it failed to parse", e);
                }
            }
        }
        this.services = HashTreePMap.from(services);
    }

    @Override
    public CompletionStage<Optional<URI>> locate(String name, Descriptor.Call<?, ?> serviceCall) {
        return locateAll(name, serviceCall)
            .thenApply(uris -> uris.stream().findFirst());
    }

    @Override
    public CompletionStage<List<URI>> locateAll(String name, Descriptor.Call<?, ?> serviceCall) {
        return CompletableFuture.completedFuture(services.getOrDefault(name, Collections.emptyList()));
    }
}
