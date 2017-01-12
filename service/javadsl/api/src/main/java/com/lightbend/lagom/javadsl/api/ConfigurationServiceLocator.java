/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import play.Configuration;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * A service locator that uses static configuration.
 */
@Singleton
public class ConfigurationServiceLocator implements ServiceLocator {

    private static final String LAGOM_SERVICES_KEY = "lagom.services";
    private final PMap<String, URI> services;

    @Inject
    public ConfigurationServiceLocator(Configuration configuration) {
        Map<String, URI> services = new HashMap<>();
        if (configuration.underlying().hasPath(LAGOM_SERVICES_KEY)) {
            Config config = configuration.underlying().getConfig(LAGOM_SERVICES_KEY);
            for (String key: config.root().keySet()) {
                try {
                    String value = config.getString(key);
                    URI uri = new URI(value);
                    services.put(key, uri);
                } catch (ConfigException.WrongType e) {
                    throw new IllegalStateException("Error loading configuration for " + getClass().getSimpleName() + ". Expected lagom.services." + key + " to be a String, but was " + config.getValue(key).valueType(), e);
                } catch (URISyntaxException e) {
                    throw new IllegalStateException("Error loading configuration for  " + getClass().getSimpleName() + ". Expected lagom.services." + key + " to be a URI, but it failed to parse", e);
                }
            }
        }
        this.services = HashTreePMap.from(services);
    }

    @Override
    public CompletionStage<Optional<URI>> locate(String name, Descriptor.Call<?, ?> serviceCall) {
        return CompletableFuture.completedFuture(Optional.ofNullable(services.get(name)));
    }

    @Override
    public <T> CompletionStage<Optional<T>> doWithService(String name, Descriptor.Call<?, ?> serviceCall, Function<URI, CompletionStage<T>> block) {
        return Optional.ofNullable(services.get(name))
                .map(block.andThen(cs -> cs.thenApply(Optional::ofNullable)))
                .orElse(CompletableFuture.completedFuture(Optional.empty()));
    }
}
