/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.server

import javax.inject.Inject

import com.google.inject.Provider
import play.api.{ Configuration, Environment }
import play.api.inject.{ Binding, Module }

class ServerModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[ServiceConfig].toProvider[ServiceConfigProvider]
  )
}

class ServiceConfigProvider @Inject() (config: Configuration) extends Provider[ServiceConfig] {
  override lazy val get = {
    // FIXME: I'm unsure about how to support both http and https services in dev mode.
    val httpAddress = config.underlying.getString("play.server.http.address")
    val httpPort = config.getString("play.server.http.port").get
    val url = s"http://$httpAddress:$httpPort"

    ServiceConfig(url)
  }
}

case class ServiceConfig(url: String)
