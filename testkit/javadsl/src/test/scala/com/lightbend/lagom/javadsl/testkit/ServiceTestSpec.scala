/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.testkit

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import javax.inject.Inject
import akka.japi.function.Procedure
import com.google.inject.AbstractModule
import com.lightbend.lagom.javadsl.api.Descriptor
import com.lightbend.lagom.javadsl.api.Service
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport
import com.lightbend.lagom.javadsl.testkit.ServiceTest.Setup
import com.lightbend.lagom.javadsl.testkit.ServiceTest.TestServer
import org.scalatest.Matchers
import org.scalatest.WordSpec
import play.inject.guice.GuiceApplicationBuilder

import scala.collection.JavaConverters._
import scala.compat.java8.FunctionConverters._
import scala.util.Properties

class ServiceTestSpec extends WordSpec with Matchers {
  "ServiceTest" when {
    "started with Cassandra" should {
      "create a temporary directory" in {
        val temporaryFileCountBeforeRun = listTemporaryFiles().size

        withServer(ServiceTest.defaultSetup.withCassandra()) { _ =>
          val temporaryFilesDuringRun = listTemporaryFiles()

          temporaryFilesDuringRun should have size (temporaryFileCountBeforeRun + 1)
        }
      }
    }

    "stopped after starting" should {
      "remove its temporary directory" in {
        val temporaryFileCountBeforeRun = listTemporaryFiles().size

        withServer(ServiceTest.defaultSetup.withCassandra()) { _ =>
          ()
        }

        val temporaryFilesAfterRun = listTemporaryFiles()

        temporaryFilesAfterRun should have size temporaryFileCountBeforeRun
      }
    }

    "started with JDBC" should {
      "start successfully" in {
        withServer(ServiceTest.defaultSetup.withJdbc()) { _ =>
          ()
        }
      }
    }
  }

  def withServer(setup: Setup)(block: TestServer => Unit): Unit = {
    ServiceTest.withServer(setup.configureBuilder((registerService _).asJava), block(_))
  }

  def registerService(builder: GuiceApplicationBuilder): GuiceApplicationBuilder =
    builder.bindings(new TestServiceModule)

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

class TestServiceImpl @Inject() (persistentEntityRegistry: PersistentEntityRegistry) extends TestService

class TestServiceModule extends AbstractModule with ServiceGuiceSupport {
  override def configure(): Unit = bindService(classOf[TestService], classOf[TestServiceImpl])
}
