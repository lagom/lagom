/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.client;

import akka.actor.ActorSystem;
import akka.discovery.Lookup;
import akka.discovery.ServiceDiscovery;
import akka.discovery.SimpleServiceDiscovery;
import com.lightbend.lagom.internal.client.ServiceNameParser;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class AkkaDiscoveryServiceLocator extends CircuitBreakingServiceLocator {


    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final SimpleServiceDiscovery serviceDiscovery;
    private final Config config;

    @Inject
    public AkkaDiscoveryServiceLocator(CircuitBreakersPanel circuitBreakersPanel, ActorSystem actorSystem) {
        super(circuitBreakersPanel);
        this.serviceDiscovery = ServiceDiscovery.get(actorSystem).discovery();
        this.config = actorSystem.settings().config();
    }

    @Override
    public CompletionStage<List<URI>> locateAll(String name, Descriptor.Call<?, ?> serviceCall) {

        logger.debug("Lookup for service: " + name);
        Lookup lookupQuery = patchLookupIfNeeded(ServiceNameParser.toLookupQuery(name));
        logger.debug("query -> " + lookupQuery);

        CompletionStage<SimpleServiceDiscovery.Resolved> lookup =
                serviceDiscovery.lookup(lookupQuery, Duration.ofSeconds(5));

        return lookup.thenCompose(resolved -> {
            logger.debug("Retrieved address: " + resolved.getAddresses());
            List<URI> uris =
                    resolved.getAddresses()
                            .stream()
                            .map(this::toURI)
                            .collect(Collectors.toList());

            return CompletableFuture.completedFuture(uris);
        });
    }

    /**
     * Patch Lookup to use 'http' as portName and 'tcp' as protocol
     * if not assigned and not Kafka or Cassandra lookups.
     */
    private Lookup patchLookupIfNeeded(Lookup lookup) {

        if(lookup.portName().isEmpty() && lookup.protocol().isEmpty()) {
            Config discoveryConfig = config.getConfig("lagom.akka.discovery.defaults");

            String serviceName = discoveryConfig.getString("prefix") +
                    lookup.serviceName() +
                    discoveryConfig.getString("suffix");

            Lookup lookupPatched =  Lookup.create(serviceName)
                    .withPortName(discoveryConfig.getString("port-name"))
                    .withProtocol(discoveryConfig.getString("protocol"));

            logger.debug("Patched: " + lookup + " -> " + lookupPatched);
            return lookupPatched;
        }
        else
            return lookup;
    }


    @Override
    public CompletionStage<Optional<URI>> locate(String name, Descriptor.Call<?, ?> serviceCall) {
        return locateAll(name, serviceCall).thenApply(this::selectRandomURI);
    }

    private URI toURI(SimpleServiceDiscovery.ResolvedTarget resolvedTarget) {

        // it's safe to call 'get' here, those have already been validated in #filterValid
        int port = resolvedTarget.getPort().map(i -> (Integer) i).orElseGet(() -> -1);
        try {
            return new URI(
                    "http", // scheme
                    null, // userInfo
                    resolvedTarget.host(), // host
                    port, // port
                    null, // path
                    null, // query
                    null // fragment
            );

        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<URI> selectRandomURI(List<URI> uris) {
        if (uris.isEmpty()) {
            return Optional.empty();
        } else {
            int index = ThreadLocalRandom.current().nextInt(uris.size());
            return Optional.of(uris.get(index));
        }
    }
}
