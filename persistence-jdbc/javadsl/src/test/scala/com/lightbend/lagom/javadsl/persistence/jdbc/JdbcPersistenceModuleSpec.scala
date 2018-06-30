/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jdbc

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import com.lightbend.lagom.internal.javadsl.api.broker.TopicFactory
import com.lightbend.lagom.javadsl.api.ServiceLocator
import com.typesafe.config.ConfigFactory
import javax.inject.Inject
import org.scalatest._
import play.api.Play
import play.api.db.DBApi
import play.api.inject.{ ApplicationLifecycle, DefaultApplicationLifecycle }
import play.inject.guice.GuiceApplicationBuilder
import play.api.inject.{ ApplicationLifecycle, BindingKey, DefaultApplicationLifecycle, bind => sBind }

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
class JdbcPersistenceModuleSpec
  extends AsyncWordSpec
  with Matchers
  with BeforeAndAfterAll {

  "The JdbcPersistenceModule" should {
    "wire Lagom's specific LagomDbApiProvider without throwing any error even in the absence of a properly configured DB" in {

      val lifecycle = new DefaultApplicationLifecycle

      // bogus DB configurations
      val dbConfig =
        """
          |db.default {
          |  driver = "org.bogus.Driver"
          |  url = "jdbc:bogus://localhost:1234/bogus"
          |  user = "bogus"
          |  password = "bogus"
          }
        """.stripMargin

      // this would blow up if we were eagerly creating DB connections
      val app =
        new GuiceApplicationBuilder()
          .overrides(sBind[ApplicationLifecycle].to(lifecycle))
          .bindings(sBind[DbWrapper].toSelf)
          .configure(ConfigFactory.parseString(dbConfig))
          .build()

      app.getWrappedApplication.stop().map(_ => Succeeded)
    }
  }

}

// this little class will force Guice to wire the DBApi
// and so we can prove that it was created but not connection attempt was made
class DbWrapper @Inject() (val dbApi: DBApi)
