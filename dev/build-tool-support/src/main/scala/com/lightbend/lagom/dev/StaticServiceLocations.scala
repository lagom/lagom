/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.dev

private[lagom] object StaticServiceLocations {
  def staticServiceLocations(lagomCassandraPort: Int, lagomKafkaAddress: String): Map[String, String] = {
    Map(
      "cas_native" -> s"tcp://127.0.0.1:$lagomCassandraPort/cas_native"
    )
  }
}
