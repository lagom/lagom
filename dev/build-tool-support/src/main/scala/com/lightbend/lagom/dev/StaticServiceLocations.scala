/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.dev

private[lagom] object StaticServiceLocations {
  def staticServiceLocations(lagomCassandraPort: Int, lagomKafkaAddress: String): Map[String, String] = {
    Map(
      "cas_native"   -> s"tcp://127.0.0.1:$lagomCassandraPort/cas_native",
      "kafka_native" -> s"tcp://$lagomKafkaAddress/kafka_native"
    )
  }
}
