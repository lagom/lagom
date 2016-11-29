/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.discovery.impl;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import akka.actor.ActorRef;
import akka.pattern.PatternsCS;
import akka.util.Timeout;
import com.google.inject.Inject;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistry;
import com.lightbend.lagom.discovery.ServiceRegistryActor.*;

import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistryService;
import play.Logger;
import play.Logger.ALogger;
import scala.Option;
import scala.compat.java8.OptionConverters;
import scala.concurrent.duration.Duration;

import javax.inject.Named;

import org.pcollections.PSequence;

import com.lightbend.lagom.internal.javadsl.registry.RegisteredService;

public class ServiceRegistryImpl implements ServiceRegistry {

	private static final ALogger LOGGER = Logger.of(ServiceRegistryImpl.class);

	private final Timeout timeout = new Timeout(Duration.create(5, TimeUnit.SECONDS));
	private final ActorRef registry;

	@Inject
	public ServiceRegistryImpl(@Named(ServiceRegistryModule.SERVICE_REGISTRY_ACTOR) ActorRef registry) {
		this.registry = registry;
	}

	@Override
	public ServiceCall<ServiceRegistryService, NotUsed> register(String name) {
		return service -> {
			if (LOGGER.isDebugEnabled())
				LOGGER.debug("register invoked, name=[" + name + "], request=[" + service + "]");
      return PatternsCS.ask(registry, new Register(name, service), timeout)
					.thenApply(done -> NotUsed.getInstance());
		};
	}

	@Override
	public ServiceCall<NotUsed, NotUsed> unregister(String name) {
		return request -> {
			if (LOGGER.isDebugEnabled())
				LOGGER.debug("unregister invoked, name=[" + name + "], request=[" + request + "]");
			registry.tell(new Remove(name), null);
			return CompletableFuture.completedFuture(NotUsed.getInstance());
		};
	}

	@Override
	public ServiceCall<NotUsed, URI> lookup(String name) {
		return request -> {
			if (LOGGER.isDebugEnabled())
				LOGGER.debug("locate invoked, name=[" + name + "], request=[" + request + "]");
			return PatternsCS.ask(registry, new Lookup(name), timeout).thenApply(result -> {
				@SuppressWarnings("unchecked")
				Optional<URI> location = OptionConverters.toJava((Option<URI>) result);
				logServiceLookupResult(name, location);
				if (location.isPresent()) {
					return location.get();
				} else {
					throw new com.lightbend.lagom.javadsl.api.transport.NotFound(name);
				}
			});
		};
	}

	@SuppressWarnings("unchecked")
	@Override
	public ServiceCall<NotUsed, PSequence<RegisteredService>> registeredServices() {
		return unusedRequest -> {
			return PatternsCS.ask(registry, GetRegisteredServices$.MODULE$, timeout)
					.thenApply( result -> {
						RegisteredServices registeredServices = (RegisteredServices) result;
						return registeredServices.services();
				    });
		};
	}

	private void logServiceLookupResult(String name, Optional<URI> location) {
		if (LOGGER.isDebugEnabled()) {
			if (location.isPresent())
				LOGGER.debug("Location of service name=[" + name + "] is " + location.get());
			else
				LOGGER.debug("Service name=[" + name + "] has not been registered");
		}
	}
}
