/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package ${package}.${service1Name}.impl;

import akka.Done;
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import ${package}.${service1Name}.api.GreetingMessage;
import ${package}.${service1Name}.api.${service1ClassName}Service;
import ${package}.${service1Name}.impl.${service1ClassName}Command.*;

/**
 * Implementation of the ${service1ClassName}Service.
 */
public class ${service1ClassName}ServiceImpl implements ${service1ClassName}Service {

  private final PersistentEntityRegistry persistentEntityRegistry;

  @Inject
  public ${service1ClassName}ServiceImpl(PersistentEntityRegistry persistentEntityRegistry) {
    this.persistentEntityRegistry = persistentEntityRegistry;
    persistentEntityRegistry.register(${service1ClassName}Entity.class);
  }

  @Override
  public ServiceCall<NotUsed, String> hello(String id) {
    return request -> {
      // Look up the hello world entity for the given ID.
      PersistentEntityRef<${service1ClassName}Command> ref = persistentEntityRegistry.refFor(${service1ClassName}Entity.class, id);
      // Ask the entity the Hello command.
      return ref.ask(new Hello(id, Optional.empty()));
    };
  }

  @Override
  public ServiceCall<GreetingMessage, Done> useGreeting(String id) {
    return request -> {
      // Look up the hello world entity for the given ID.
      PersistentEntityRef<${service1ClassName}Command> ref = persistentEntityRegistry.refFor(${service1ClassName}Entity.class, id);
      // Tell the entity to use the greeting message specified.
      return ref.ask(new UseGreetingMessage(request.message));
    };

  }

}
