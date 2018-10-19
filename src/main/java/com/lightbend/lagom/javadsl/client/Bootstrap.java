/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.client;

import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.management.AkkaManagement;
import akka.management.cluster.bootstrap.ClusterBootstrap;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Bootstrap {

    @Inject
    public Bootstrap(ActorSystem actorSystem) {
        new AkkaManagement((ExtendedActorSystem) actorSystem).start();
        new ClusterBootstrap((ExtendedActorSystem) actorSystem).start();
    }

}
