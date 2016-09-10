/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.kafka

object KafkaLauncher {
  def main(args: Array[String]): Unit = {
    val kafkaServerPort: Int =
      if (args.length > 0) args(0).toInt
      else Integer.getInteger("KafkaServer.port", KafkaLocalServer.DefaultPort)

    val zookeperServerPort: Int =
      if (args.length > 1) args(1).toInt
      else Integer.getInteger("ZooKeeperServer.port", KafkaLocalServer.ZooKeperLocalServer.DefaultPort)

    val targetDir: Option[String] =
      if (args.length > 2) Some(args(2))
      else None

    val kafkaPropertiesFile: String =
      if (args.length > 3) args(3)
      else System.getProperty("Kafka.propertiesFile", KafkaLocalServer.DefaultPropertiesFile)

    val kafkaServer = KafkaLocalServer(kafkaServerPort, zookeperServerPort, kafkaPropertiesFile, targetDir)
    kafkaServer.start()
  }
}
