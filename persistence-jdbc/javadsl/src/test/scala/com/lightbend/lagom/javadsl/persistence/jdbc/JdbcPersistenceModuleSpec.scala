/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.persistence.jdbc

import javax.inject.Inject
import org.scalatest._
import play.api.PlayException
import play.api.db.DBApi
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.{ bind => playBind }

/**
 * This test will simply wire a minimal application using Guice.
 *
 * Lagom used to override default Play configuration to fail on startup when
 * unable to connect to a configured database.
 * As of Play 2.7.0, it no longer fails on startup by default.
 * This test remains to ensure that Lagom doesn't introduce a startup failure.
 */
class JdbcPersistenceModuleSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll {
  "The JdbcPersistenceModule" should {
    "should start the service when database is not available" in {
      // Should be okay to build an application since Lagom configuration
      // enables it without a database being available.
      val app =
        new GuiceApplicationBuilder()
          .bindings(playBind[DbWrapper].toSelf)
          .configure(
            // Correct configuration, but the database is not available
            "db.default.driver"                             -> "org.h2.Driver",
            "db.default.url"                                -> "jdbc:h2:tcp://localhost/~/notavailable",
            "lagom.cluster.exit-jvm-when-system-terminated" -> "off",
            "lagom.cluster.bootstrap.enabled"               -> "off"
          )
          .build()

      val dbWrapper = app.injector.instanceOf[DbWrapper]
      dbWrapper should not be (null)

      app.stop().map(_ => succeed)
    }

    "should fail to start the service when database is not available and configured to fail fast" in {
      assertThrows[PlayException] {
        new GuiceApplicationBuilder()
          .bindings(playBind[DbWrapper].toSelf)
          .configure(
            // Correct configuration, but the database is not available
            "db.default.driver" -> "org.h2.Driver",
            "db.default.url"    -> "jdbc:h2:tcp://localhost/~/notavailable",
            // And it is configured to fail fast
            "play.db.prototype.hikaricp.initializationFailTimeout" -> "1",
            "lagom.cluster.exit-jvm-when-system-terminated"        -> "off",
            "lagom.cluster.bootstrap.enabled"                      -> "off"
          )
          .build()
      }
    }
  }
}

// So that we can confirm DBApi was created
class DbWrapper @Inject() (val dbApi: DBApi)
