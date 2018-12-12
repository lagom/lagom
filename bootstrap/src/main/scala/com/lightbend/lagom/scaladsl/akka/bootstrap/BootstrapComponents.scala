package com.lightbend.lagom.scaladsl.akka.bootstrap

import akka.actor.ActorSystem
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap


/**
  * Mix this into your application cake to be able to bootstrap your Akka cluster.
  */
trait AkkaDiscoveryBootstrap {

  def actorSystem: ActorSystem

  /**
    * You must invoke this to start cluster bootstrap.
    */
  def startClusterBootstrap(): Unit = {
    ClusterBootstrap(actorSystem).start()
  }

  /**
    * You must invoke this to start Akka management.
    */
  def startAkkaManagement(): Unit = {
    AkkaManagement(actorSystem).start()
  }
}