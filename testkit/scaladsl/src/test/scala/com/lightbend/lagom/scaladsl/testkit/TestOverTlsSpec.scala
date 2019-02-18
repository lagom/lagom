package com.lightbend.lagom.scaladsl.testkit

import java.nio.file.{ Files, Path, Paths }

import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service }
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.persistence.jdbc.JdbcPersistenceComponents
import com.lightbend.lagom.scaladsl.persistence.{ PersistenceComponents, PersistentEntityRegistry }
import com.lightbend.lagom.scaladsl.playjson.{ EmptyJsonSerializerRegistry, JsonSerializerRegistry }
import com.lightbend.lagom.scaladsl.server.{ LagomApplication, LagomApplicationContext, LagomServer, LocalServiceLocator }
import org.scalatest.{ Matchers, WordSpec }
import play.api.db.HikariCPComponents
import play.api.libs.ws.ahc.AhcWSComponents

import scala.util.Properties

/**
  *
  */
class TestOverTlsSpec extends WordSpec with Matchers {
  "TestOverTls" when {
    "started with ssl" should {
      "provide an ssl port" in {
        ServiceTest.withServer(ServiceTest.defaultSetup.withSsl())(new TestTlsApplication(_)) { server =>
          server.playServer.httpsPort
        }
      }

      "provide an ssl context for the client" in {
      }
    }
  }
}

trait TestTlsService extends Service {

  import Service._

  override final def descriptor: Descriptor = named("test-tls")

}

class TestTlsServiceImpl() extends TestTlsService

class TestTlsApplication(context: LagomApplicationContext) extends LagomApplication(context)
  with LocalServiceLocator
  with AhcWSComponents {

  override lazy val lagomServer: LagomServer = serverFor[TestTlsService](new TestTlsServiceImpl())

}

