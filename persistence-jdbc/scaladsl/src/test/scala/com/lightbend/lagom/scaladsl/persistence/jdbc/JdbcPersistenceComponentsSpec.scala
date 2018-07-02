/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.jdbc

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, Materializer }
import com.lightbend.lagom.scaladsl.playjson.{ EmptyJsonSerializerRegistry, JsonSerializerRegistry }
import com.typesafe.config.ConfigFactory
import org.scalatest._
import play.api.ApplicationLoader.Context
import play.api.{ BuiltInComponentsFromContext, Configuration, Environment }
import play.api.db.{ DBComponents, HikariCPComponents }
import play.api.inject.{ ApplicationLifecycle, DefaultApplicationLifecycle }
import play.api.mvc.EssentialFilter
import play.api.routing.Router
import play.core.DefaultWebCommands

import scala.concurrent.{ ExecutionContext, Future }

/**
 * This test will simply wire a minimal application using Guice.
 *
 * The LagomDbApiProvider is a copy from the Play that does not try to connect to the DB
 * when created.
 *
 * In this particular test, we explicitly set a bogus DB configuration and let Guice create the class
 * and wire then together. Because the Lagom impl does not connect eagerly no error should be thrown
 * after wiring and the test should just pass.
 */
class JdbcPersistenceComponentsSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll {

  "The JdbcPersistenceComponents" should {
    "wire Lagom's specific LagomDBComponents without throwing any error even in the absence of a properly configured DB" in {

      val akkaConfig = ConfigFactory.parseString(
        """
          |akka.actor.provider = akka.cluster.ClusterActorRefProvider
          |lagom.defaults.cluster.join-self = off
        """.stripMargin
      )

      // bogus DB configurations
      val dbConfig = ConfigFactory.parseString(
        """
          | db.default {
          |  driver = "org.bogus.Driver"
          |  url = "jdbc:bogus://localhost:1234/bogus"
          |  user = "bogus"
          |  password = "bogus"
          | }
        """.stripMargin
      )
      val config = dbConfig.withFallback(akkaConfig).withFallback(ConfigFactory.load())

      // this would blow up if we were eagerly creating DB connections
      val app =
        new JdbcPersistenceComponents with HikariCPComponents {
          override val environment: Environment = Environment.simple()
          override val actorSystem: ActorSystem = ActorSystem("test", config)
          override val materializer: Materializer = ActorMaterializer.create(actorSystem)
          override val executionContext: ExecutionContext = actorSystem.dispatcher
          override val configuration: Configuration = Configuration(config)
          override def applicationLifecycle: ApplicationLifecycle = new DefaultApplicationLifecycle
          override def jsonSerializerRegistry: JsonSerializerRegistry = EmptyJsonSerializerRegistry
        }

      // dbApi is a lazy field, we need to access it to trigger initialization
      app.dbApi
      app.actorSystem.terminate().map { _ =>
        // finalize the test when app stops
        Succeeded
      }
    }

  }
}
