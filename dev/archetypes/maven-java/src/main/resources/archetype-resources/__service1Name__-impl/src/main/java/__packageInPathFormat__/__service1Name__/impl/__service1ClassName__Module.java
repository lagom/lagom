/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package ${package}.${service1Name}.impl;

import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import ${package}.${service1Name}.api.${service1ClassName}Service;

/**
 * The module that binds the ${service1ClassName}Service so that it can be served.
 */
public class ${service1ClassName}Module extends AbstractModule implements ServiceGuiceSupport {
  @Override
  protected void configure() {
    bindServices(serviceBinding(${service1ClassName}Service.class, ${service1ClassName}ServiceImpl.class));
  }
}
