/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package ${package}.${service2Name}.impl;

import akka.NotUsed;
import akka.stream.javadsl.Source;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import ${package}.${service1Name}.api.${service1ClassName}Service;
import ${package}.${service2Name}.api.${service2ClassName}Service;

import javax.inject.Inject;

import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * Implementation of the HelloString.
 */
public class ${service2ClassName}ServiceImpl implements ${service2ClassName}Service {

  private final ${service1ClassName}Service ${service1Name}Service;

  @Inject
  public ${service2ClassName}ServiceImpl(${service1ClassName}Service ${service1Name}Service) {
    this.${service1Name}Service = ${service1Name}Service;
  }

  @Override
  public ServiceCall<Source<String, NotUsed>, Source<String, NotUsed>> stream() {
    return hellos -> completedFuture(
        hellos.mapAsync(8, name -> ${service1Name}Service.hello(name).invoke()));
  }
}
