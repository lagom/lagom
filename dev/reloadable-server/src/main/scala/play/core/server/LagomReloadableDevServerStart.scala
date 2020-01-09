/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package play.core.server

import java.io.File

import akka.actor.ActorSystem
import com.lightbend.lagom.devmode.ssl.LagomDevModeSSLHolder
import play.api.Configuration
import play.core.BuildLink

import scala.collection.JavaConverters._

/**
 * Used to start servers in 'dev' mode, a mode where the application
 * is reloaded whenever its source changes.
 */
object LagomReloadableDevServerStart {

  /**
   * Provides a server for the dev environment.  Use -1 to opt out of HTTP or HTTPS.
   *
   * <p>This method uses simple Java types so that it can be used with reflection by code
   * compiled with different versions of Scala.
   */
  def mainDev(
      buildLink: BuildLink,
      httpAddress: String,
      httpPort: Int,
      httpsPort: Int,
  ): ReloadableServer = {
    val enableSsl = httpsPort > 0

    // The pairs play.server.httpx.{address,port} are read from PlayRegisterWithServiceRegistry
    // to register the service
    val httpsSettings: Map[String, String] =
      if (enableSsl) {
        val sslHolder = new LagomDevModeSSLHolder(new File(".")) // hard-coded

        Map(
          // In dev mode, `play.server.https.address` and `play.server.http.address` are assigned the same value
          // but both settings are set in case some code specifically read one config setting or the other.
          "play.server.https.address" -> httpAddress, // there's no httpsAddress
          "play.server.https.port"    -> httpsPort.toString,
          // See also com/lightbend/lagom/scaladsl/testkit/ServiceTest.scala
          // These configure the server
          "play.server.https.keyStore.path"     -> sslHolder.keyStoreMetadata.storeFile.getAbsolutePath,
          "play.server.https.keyStore.type"     -> sslHolder.keyStoreMetadata.storeType,
          "play.server.https.keyStore.password" -> String.valueOf(sslHolder.keyStoreMetadata.storePassword),
          // These configure the clients (play-ws and akka-grpc)
          "ssl-config.loose.disableHostnameVerification" -> "true",
          "ssl-config.trustManager.stores.0.path"        -> sslHolder.trustStoreMetadata.storeFile.getAbsolutePath,
          "ssl-config.trustManager.stores.0.type"        -> sslHolder.trustStoreMetadata.storeType,
          "ssl-config.trustManager.stores.0.password"    -> String.valueOf(sslHolder.trustStoreMetadata.storePassword)
        )
      } else Map.empty

    val httpSettings: Map[String, String] =
      Map(
        // The pairs play.server.httpx.{address,port} are read from PlayRegisterWithServiceRegistry
        // to register the service
        "play.server.http.address" -> httpAddress,
        "play.server.http.port"    -> httpPort.toString
      )

    // each user service needs to tune its "play.filters.hosts.allowed" so that Play's
    // AllowedHostFilter (https://www.playframework.com/documentation/2.6.x/AllowedHostsFilter)
    // doesn't block request with header "Host: " with a value "localhost:<someport>". The following
    // setting whitelists 'localhost` for both http/s ports and also 'httpAddress' for both ports too.
    val allowHostsSetting = "play.filters.hosts.allowed" -> {
      val http  = List(s"$httpAddress:$httpPort", s"localhost:$httpPort")
      val https = if (enableSsl) List(s"$httpAddress:$httpsPort", s"localhost:$httpsPort") else Nil
      (http ++ https).asJavaCollection
    }

    // on dev-mode, we often have more than one cluster on the same jvm
    val clusterSettings = "akka.cluster.jmx.multi-mbeans-in-same-jvm" -> "on"

    val additionalSettings = httpSettings ++ httpsSettings + allowHostsSetting + clusterSettings

    new DevServerStart(mkServerActorSystem, additionalSettings)
      .mainDev(buildLink, httpAddress, httpPort, httpsPort)
  }

  private def mkServerActorSystem(conf: Configuration) = {
    val devModeAkkaConfig = conf.underlying.getConfig("lagom.akka.dev-mode.config")
    val actorSystemName   = conf.underlying.getString("lagom.akka.dev-mode.actor-system.name")
    ActorSystem(actorSystemName, devModeAkkaConfig)
  }
}
