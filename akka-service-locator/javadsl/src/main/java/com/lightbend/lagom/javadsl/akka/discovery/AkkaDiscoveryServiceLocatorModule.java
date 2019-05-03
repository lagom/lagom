/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.akka.discovery;

import com.lightbend.lagom.javadsl.api.ServiceLocator;
import com.typesafe.config.Config;
import play.Environment;
import play.inject.Module;

import java.util.Collections;
import java.util.List;

/** Module providing the Akka Discovery based Lagom {@link ServiceLocator}. */
public class AkkaDiscoveryServiceLocatorModule extends Module {
  @Override
  public List<play.inject.Binding<?>> bindings(Environment environment, Config config) {
    if (environment.isProd()) {
      return Collections.singletonList(
          bindClass(ServiceLocator.class).to(AkkaDiscoveryServiceLocator.class));
    }
    return Collections.emptyList();
  }
}
