/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.devmode.internal.registry

import java.net.URI

import com.typesafe.config.Config

import scala.collection.immutable

package object registry {
  private[lagom] def serviceDnsRecords(config: Config): immutable.Seq[URI] = {
    val uris = immutable.Seq.newBuilder[URI]

    // In dev mode, `play.server.http.address` is used for both HTTP and HTTPS.
    // Reading one value or the other gets the same result.
    val httpAddress = config.getString("play.server.http.address")

    val httpPort = config.getString("play.server.http.port")
    uris += new URI(s"http://$httpAddress:$httpPort")

    if (config.hasPath("play.server.https.port")) {
      val httpsPort = config.getString("play.server.https.port")
      uris += new URI(s"https://$httpAddress:$httpsPort")
    }

    uris.result()
  }
}
