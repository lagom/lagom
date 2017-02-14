/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package ${package}.${service2Name}.impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import ${package}.${service1Name}.api.${service1ClassName}Service;
import ${package}.${service2Name}.api.${service2ClassName}Service;

/**
 * The module that binds the ${service2ClassName}Service so that it can be served.
 */
public class ${service2ClassName}Module extends AbstractModule implements ServiceGuiceSupport {
  @Override
  protected void configure() {
    // Bind the ${service2ClassName}Service service
    bindServices(serviceBinding(${service2ClassName}Service.class, ${service2ClassName}ServiceImpl.class));
    // Bind the ${service1ClassName}Service client
    bindClient(${service1ClassName}Service.class);
  }
}
