/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.akka.discovery;

import akka.actor.ActorSystem;
import akka.discovery.Discovery;
import com.lightbend.lagom.internal.client.AkkaDiscoveryHelper;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.client.CircuitBreakersPanel;
import com.lightbend.lagom.javadsl.client.CircuitBreakingServiceLocator;
import scala.collection.JavaConverters;
import scala.compat.java8.FutureConverters;
import scala.compat.java8.OptionConverters;

import javax.inject.Inject;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Akka Discovery based implementation of the {@link com.lightbend.lagom.javadsl.api.ServiceLocator}.
 */
public class AkkaDiscoveryServiceLocator extends CircuitBreakingServiceLocator {
    private final AkkaDiscoveryHelper helper;

    @Inject
    public AkkaDiscoveryServiceLocator(CircuitBreakersPanel circuitBreakersPanel, ActorSystem actorSystem) {
        super(circuitBreakersPanel);
        this.helper = new AkkaDiscoveryHelper(
                actorSystem.settings().config().getConfig("lagom.akka.discovery"),
                Discovery.get(actorSystem).discovery(), actorSystem.dispatcher());
    }

    @Override
    public CompletionStage<List<URI>> locateAll(String name, Descriptor.Call<?, ?> serviceCall) {
        return FutureConverters.toJava(helper.locateAll(name))
            .thenApply(xs -> JavaConverters.seqAsJavaListConverter(xs).asJava());
    }

    @Override
    public CompletionStage<Optional<URI>> locate(String name, Descriptor.Call<?, ?> serviceCall) {
        return FutureConverters.toJava(helper.locate(name))
            .thenApply(OptionConverters::toJava);
    }
}
