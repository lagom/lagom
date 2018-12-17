package com.lightbend.lagom.javadsl.akka.bootstrap;

import play.api.Configuration;
import play.api.Environment;
import play.api.inject.Binding;
import play.api.inject.Module;
import scala.collection.Seq;

/**
 * Module providing bootstrapping of Akka Cluster Bootstrap and Akka Management.
 */
public class AkkaClusterBootstrapModule extends Module {
    @Override
    public Seq<Binding<?>> bindings(Environment environment, Configuration configuration) {
        if (environment.mode().asJava() == play.Mode.PROD) {
            // Only cluster Bootstrap in Production
            return seq(
                bind(Bootstrap.class).toSelf().eagerly()
            );
        } else {
            return seq();
        }
    }
}
