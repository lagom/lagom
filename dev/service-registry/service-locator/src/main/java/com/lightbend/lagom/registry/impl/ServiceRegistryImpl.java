/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.registry.impl;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistry;
import com.lightbend.lagom.registry.impl.ServiceRegistryActor.*;

import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.compat.java8.OptionConverters;

import javax.inject.Inject;
import javax.inject.Named;

import org.pcollections.PSequence;

import com.lightbend.lagom.internal.javadsl.registry.RegisteredService;

public class ServiceRegistryImpl implements ServiceRegistry {

  private static final Logger LOGGER = LoggerFactory.getLogger(ServiceRegistryImpl.class);

  private final Duration timeout = Duration.ofSeconds(5);
  private final ActorRef registry;

  @Inject
  public ServiceRegistryImpl(
      @Named(ServiceRegistryModule.SERVICE_REGISTRY_ACTOR) ActorRef registry) {
    this.registry = registry;
  }

  @Override
  public ServiceCall<ServiceRegistryService, NotUsed> register(String name) {
    return service -> {
      if (LOGGER.isDebugEnabled())
        LOGGER.debug("register invoked, name=[" + name + "], request=[" + service + "]");
      return Patterns.ask(registry, new Register(name, service), timeout)
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
  public ServiceCall<NotUsed, URI> lookup(String serviceName, Optional<String> portName) {
    return request -> {
      if (LOGGER.isDebugEnabled())
        LOGGER.debug(
            "locate invoked, name=["
                + serviceName
                + "] and portName=["
                + portName
                + "] . request=["
                + request
                + "]");
      return Patterns.ask(
              registry, new Lookup(serviceName, OptionConverters.toScala(portName)), timeout)
          .thenApply(
              result -> {
                @SuppressWarnings("unchecked")
                Optional<URI> location = OptionConverters.toJava((Option<URI>) result);
                logServiceLookupResult(serviceName, location);
                if (location.isPresent()) {
                  return location.get();
                } else {
                  throw new com.lightbend.lagom.javadsl.api.transport.NotFound(
                      "Can't find service " + serviceName + " with port" + portName);
                }
              });
    };
  }

  @SuppressWarnings("unchecked")
  @Override
  public ServiceCall<NotUsed, PSequence<RegisteredService>> registeredServices() {
    return unusedRequest -> {
      return Patterns.ask(registry, GetRegisteredServices$.MODULE$, timeout)
          .thenApply(
              result -> {
                RegisteredServices registeredServices = (RegisteredServices) result;
                return registeredServices.services();
              });
    };
  }

  private void logServiceLookupResult(String name, Optional<URI> location) {
    if (LOGGER.isDebugEnabled()) {
      if (location.isPresent())
        LOGGER.debug("Location of service name=[" + name + "] is " + location.get());
      else LOGGER.debug("Service name=[" + name + "] has not been registered");
    }
  }
}
