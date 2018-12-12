/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.akka.bootstrap;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.management.AkkaManagement;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import com.typesafe.config.Config;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Bootstrap {

    @Inject
    public Bootstrap(ActorSystem actorSystem) {
        Config config = actorSystem.settings().config().getConfig("lagom.akka.bootstrap");
        if (config.getBoolean("start-akka-management")) {
            new AkkaManagement((ExtendedActorSystem) actorSystem).start();
        }
        if (config.getBoolean("start-cluster-bootstrap")) {
            new ClusterBootstrap((ExtendedActorSystem) actorSystem).start();
        }
    }

}
