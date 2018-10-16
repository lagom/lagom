/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.client;

import akka.actor.ActorSystem;
import akka.discovery.Lookup;
import com.lightbend.lagom.internal.client.ServiceLocatorConfig;
import com.lightbend.lagom.internal.client.ServiceConfigEntry;
import com.lightbend.lagom.javadsl.api.Descriptor;

import akka.discovery.ServiceDiscovery;
import akka.discovery.SimpleServiceDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.InetAddress;
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
    private final ServiceLocatorConfig serviceLocatorConfig;

    @Inject
    public AkkaDiscoveryServiceLocator(CircuitBreakersPanel circuitBreakersPanel, ActorSystem actorSystem) {
        super(circuitBreakersPanel);
        this.serviceDiscovery = ServiceDiscovery.get(actorSystem).discovery();
        this.serviceLocatorConfig = ServiceLocatorConfig.load(actorSystem.settings().config());
    }


    @Override
    public CompletionStage<Optional<URI>> locate(String name, Descriptor.Call<?, ?> serviceCall) {

        logger.debug("Lookup for service: " + name );
        Lookup lookupQuery = Lookup.create(name);

        String scheme = serviceLocatorConfig.defaultScheme();

        Optional<ServiceConfigEntry> serviceEntry = serviceLocatorConfig.lookUpJava(name);
        if (serviceEntry.isPresent()) {
            ServiceConfigEntry entry = serviceEntry.get();
            if (entry.portName().isDefined()) {
                String portName = entry.portName().get();
                logger.debug("Service " + name + " using port name: " + portName);
                lookupQuery = lookupQuery.withPortName(portName);
            }

            if (entry.scheme().isDefined()) {
                scheme = entry.scheme().get();
                logger.debug("Service " + name + " using scheme: " + scheme);
            }

        }
        else {
            String defaultPortName = serviceLocatorConfig.defaultPortName();
            logger.debug("Service " + name + " not declared in config. Port name will default to: " + defaultPortName);
            // when no service define in config, we default to portName http
            lookupQuery = lookupQuery.withPortName(defaultPortName);
        }

        CompletionStage<SimpleServiceDiscovery.Resolved> lookup =
            serviceDiscovery.lookup(lookupQuery, Duration.ofSeconds(5));

        String finalScheme = scheme;
        return lookup.thenCompose(resolved -> {

            logger.debug("Retrieved address: " + resolved.getAddresses().size());
            logger.debug("Retrieved address: " + resolved.getAddresses());

            SimpleServiceDiscovery.ResolvedTarget resolvedTarget = selectRandomTarget(filterValid(resolved.getAddresses()));
            Optional<URI> optionalURI = Optional.of(toURI(resolvedTarget, finalScheme));
            return CompletableFuture.completedFuture(optionalURI);
        });
    }

    private URI toURI(SimpleServiceDiscovery.ResolvedTarget resolvedTarget, String scheme) {

        // it's safe to call 'get' here, those have already been validated in #filterValid
        int port = resolvedTarget.getPort().map(i -> (Integer) i).get();
        InetAddress address = resolvedTarget.getAddress().get();
        try {
            return new URI(
                scheme, // scheme
                null, // userInfo
                address.getHostAddress(), // host
                port, // port
                null, // path
                null, // query
                null // fragment
            );

        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private List<SimpleServiceDiscovery.ResolvedTarget> filterValid(List<SimpleServiceDiscovery.ResolvedTarget> resolvedTargets) {


        List<SimpleServiceDiscovery.ResolvedTarget> valid =
            resolvedTargets.stream()
                .filter(target -> target.getAddress().isPresent() && target.getPort().isPresent())
                .collect(Collectors.toList());

        if (valid.isEmpty()) {
            throw new IllegalStateException("No valid address found");
        }

        return valid;
    }

    private SimpleServiceDiscovery.ResolvedTarget selectRandomTarget(List<SimpleServiceDiscovery.ResolvedTarget> resolvedTargets) {
        int index = ThreadLocalRandom.current().nextInt(resolvedTargets.size());
        return resolvedTargets.get(index);
    }
}
