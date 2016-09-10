/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.kafka

import java.util.Properties
import java.util.concurrent.atomic.AtomicReference

import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.RetryUntilElapsed
import org.apache.curator.test.TestingServer

import com.lightbend.lagom.kafka.KafkaLocalServer.ZooKeperLocalServer
import com.lightbend.lagom.util.PropertiesLoader

import kafka.server.KafkaConfig
import kafka.server.KafkaServerStartable

class KafkaLocalServer private (kafkaProperties: Properties, zooKeeperServer: ZooKeperLocalServer) {

  private val kafkaServerRef = new AtomicReference[KafkaServerStartable](null)

  def start(): Unit = {
    if (kafkaServerRef.get == null) {
      // There is a possible race condition here. However, instead of attempting to avoid it 
      // by using a lock, we are working with it and do the necessary clean up if indeed we 
      // end up creating two Kafka server instances.
      val newKafkaServer = KafkaServerStartable.fromProps(kafkaProperties)
      if (kafkaServerRef.compareAndSet(null, newKafkaServer)) {
        zooKeeperServer.start()
        val kafkaServer = kafkaServerRef.get
        kafkaServer.startup()
      } else newKafkaServer.shutdown()
    }
    // else it's already running
  }

  def stop(): Unit = {
    val kafkaServer = kafkaServerRef.getAndSet(null)
    if (kafkaServer != null) {
      kafkaServer.shutdown()
      zooKeeperServer.stop()
    }
    // else it's already stopped
  }

}

object KafkaLocalServer {
  final val DefaultPort = 9092
  final val DefaultPropertiesFile = "/kafka-server.properties"

  def apply(): KafkaLocalServer = this(DefaultPort, ZooKeperLocalServer.DefaultPort, DefaultPropertiesFile, None)

  def apply(kafkaPort: Int, zooKeperServerPort: Int, kafkaPropertiesFile: String, targetDir: Option[String]): KafkaLocalServer = {
    val kafkaProperties = PropertiesLoader.from(kafkaPropertiesFile)
    targetDir.foreach(target => kafkaProperties.setProperty("log.dirs", target + java.io.File.separator + "logs"))
    kafkaProperties.setProperty("listeners", s"PLAINTEXT://:$kafkaPort")
    kafkaProperties.setProperty("zookeeper.connect", s"localhost:$zooKeperServerPort")
    new KafkaLocalServer(kafkaProperties, new ZooKeperLocalServer(zooKeperServerPort))
  }

  private class ZooKeperLocalServer(port: Int) {
    private val zooKeeperServerRef = new AtomicReference[TestingServer](null)

    def start(): Unit = {
      if (zooKeeperServerRef.compareAndSet(null, new TestingServer(port, /*start=*/ false))) {
        val zooKeeperServer = zooKeeperServerRef.get
        zooKeeperServer.start()
        val client = CuratorFrameworkFactory.newClient(zooKeeperServer.getConnectString(), new RetryUntilElapsed(3000, 500))
        try {
          client.start()
          client.blockUntilConnected()
        } finally client.close()
      }
      // else it's already running
    }

    def stop(): Unit = {
      val zooKeeperServer = zooKeeperServerRef.getAndSet(null)
      if (zooKeeperServer != null)
        zooKeeperServer.stop()
      // else it's already stopped
    }
  }

  object ZooKeperLocalServer {
    final val DefaultPort = 2181
  }
}
