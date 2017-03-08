/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.kafka

object KafkaLauncher {
  def main(args: Array[String]): Unit = {
    val kafkaServerPort: Int =
      if (args.length > 0) args(0).toInt
      else Integer.getInteger("KafkaServer.port", KafkaLocalServer.DefaultPort)

    val zookeeperServerPort: Int =
      if (args.length > 1) args(1).toInt
      else Integer.getInteger("ZooKeeperServer.port", KafkaLocalServer.ZooKeeperLocalServer.DefaultPort)

    val targetDir: Option[String] =
      if (args.length > 2) Some(args(2))
      else None

    val kafkaCleanOnStart: Boolean = {
      def castOrDefault(value: String, default: Boolean): Boolean = {
        try value.toBoolean
        catch {
          case _: IllegalArgumentException => KafkaLocalServer.DefaultResetOnStart
        }
      }

      if (args.length > 3) castOrDefault(args(3), KafkaLocalServer.DefaultResetOnStart)
      else
        Option(System.getProperty("Kafka.cleanOnStart")).map(castOrDefault(_, KafkaLocalServer.DefaultResetOnStart)).getOrElse(KafkaLocalServer.DefaultResetOnStart)
    }

    val kafkaPropertiesFile: String =
      if (args.length > 4) args(4)
      else System.getProperty("Kafka.propertiesFile", KafkaLocalServer.DefaultPropertiesFile)

    val kafkaServer = KafkaLocalServer(kafkaServerPort, zookeeperServerPort, kafkaPropertiesFile, targetDir, kafkaCleanOnStart)

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        kafkaServer.stop()
      }
    })

    kafkaServer.start()
  }
}
