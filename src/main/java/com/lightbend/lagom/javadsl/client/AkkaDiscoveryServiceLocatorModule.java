package com.lightbend.lagom.javadsl.client;

import com.lightbend.lagom.javadsl.api.ServiceLocator;
import play.api.Configuration;
import play.api.Environment;
import play.api.inject.Binding;
import play.api.inject.Module;
import scala.collection.Seq;

public class AkkaDiscoveryServiceLocatorModule extends Module {
    @Override
    public Seq<Binding<?>> bindings(Environment environment, Configuration configuration) {
        if (environment.mode().asJava() == play.Mode.PROD) {
            // Only enable Akka Management and cluster Bootstrap in Production
            // Only use the AkkaDiscoveryServiceLocator in Production
            return seq(
                bind(Bootstrap.class).toSelf().eagerly(),
                bind(ServiceLocator.class).to(AkkaDiscoveryServiceLocator.class)
            );
        } else {
            return seq();
        }
    }
}
