/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.akka.discovery;

import com.lightbend.lagom.javadsl.api.ServiceLocator;
import play.api.Configuration;
import play.api.Environment;
import play.api.inject.Binding;
import play.api.inject.Module;
import scala.collection.Seq;

/**
 * Module providing the Akka Discovery based Lagom {@link ServiceLocator}.
 */
public class AkkaDiscoveryServiceLocatorModule extends Module {
    @Override
    public Seq<Binding<?>> bindings(Environment environment, Configuration configuration) {
        if (environment.mode().asJava() == play.Mode.PROD) {
            // Only use the AkkaDiscoveryServiceLocator in Production
            return seq(
                bind(ServiceLocator.class).to(AkkaDiscoveryServiceLocator.class)
            );
        } else {
            return seq();
        }
    }
}
