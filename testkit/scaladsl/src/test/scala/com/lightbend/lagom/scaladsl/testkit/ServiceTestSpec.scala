/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.testkit

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import com.lightbend.lagom.scaladsl.api.Descriptor
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.persistence.jdbc.JdbcPersistenceComponents
import com.lightbend.lagom.scaladsl.persistence.PersistenceComponents
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.scaladsl.playjson.EmptyJsonSerializerRegistry
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.server._
import org.scalatest.Matchers
import org.scalatest.WordSpec
import play.api.db.HikariCPComponents
import play.api.libs.ws.ahc.AhcWSComponents

import scala.collection.JavaConverters._
import scala.util.Properties

class ServiceTestSpec extends WordSpec with Matchers {
  "ServiceTest" when {
    "started with Cassandra" should {
      "create a temporary directory" in {
        val temporaryFileCountBeforeRun = listTemporaryFiles().size

        ServiceTest.withServer(ServiceTest.defaultSetup.withCassandra())(new CassandraTestApplication(_)) { _ =>
          val temporaryFilesDuringRun = listTemporaryFiles()

          temporaryFilesDuringRun should have size (temporaryFileCountBeforeRun + 1)
        }
      }
    }

    "stopped after starting" should {
      "remove its temporary directory" in {
        val temporaryFileCountBeforeRun = listTemporaryFiles().size

        ServiceTest.withServer(ServiceTest.defaultSetup.withCassandra())(new CassandraTestApplication(_)) { _ =>
          ()
        }

        val temporaryFilesAfterRun = listTemporaryFiles()

        temporaryFilesAfterRun should have size temporaryFileCountBeforeRun
      }
    }

    "started with JDBC" should {
      "start successfully" in {
        ServiceTest.withServer(ServiceTest.defaultSetup.withJdbc())(new JdbcTestApplication(_)) { _ =>
          ()
        }
      }
    }
  }

  def listTemporaryFiles(): Iterator[Path] = {
    val tmpDir = Paths.get(Properties.tmpDir)
    Files
      .newDirectoryStream(tmpDir, "ServiceTest_*")
      .iterator()
      .asScala
  }
}

trait TestService extends Service {

  import Service._

  final override def descriptor: Descriptor = named("test")

}

class TestServiceImpl(persistentEntityRegistry: PersistentEntityRegistry) extends TestService

class TestApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
    with LocalServiceLocator
    with AhcWSComponents { self: PersistenceComponents =>

  override lazy val jsonSerializerRegistry: JsonSerializerRegistry = EmptyJsonSerializerRegistry

  override lazy val lagomServer: LagomServer = serverFor[TestService](new TestServiceImpl(persistentEntityRegistry))

}

class CassandraTestApplication(context: LagomApplicationContext)
    extends TestApplication(context)
    with CassandraPersistenceComponents

class JdbcTestApplication(context: LagomApplicationContext)
    extends TestApplication(context)
    with JdbcPersistenceComponents
    with HikariCPComponents
