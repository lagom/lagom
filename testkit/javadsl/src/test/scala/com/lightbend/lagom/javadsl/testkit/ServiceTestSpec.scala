/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.testkit

import java.nio.file.{ Files, Path, Paths }
import javax.inject.Inject

import akka.japi.function.Procedure
import com.google.inject.AbstractModule
import com.lightbend.lagom.javadsl.api.{ Descriptor, Service }
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport
import com.lightbend.lagom.javadsl.testkit.ServiceTest.{ Setup, TestServer }
import org.scalatest.{ Matchers, WordSpec }
import play.inject.guice.GuiceApplicationBuilder

import scala.collection.JavaConverters._
import scala.compat.java8.FunctionConverters._
import scala.util.Properties

class ServiceTestSpec extends WordSpec with Matchers {
  "ServiceTest" when {
    "started" should {
      "create a temporary directory" in {
        val temporaryFileCountBeforeRun = listTemporaryFiles().size

        withServer(ServiceTest.defaultSetup.withCassandra()) { _ =>
          val temporaryFilesDuringRun = listTemporaryFiles()

          temporaryFilesDuringRun should have size (temporaryFileCountBeforeRun + 1)
        }
      }
    }

    "stopped after starting should" should {
      "remove its temporary directory" in {
        val temporaryFileCountBeforeRun = listTemporaryFiles().size

        withServer(ServiceTest.defaultSetup.withCassandra()) { _ => () }

        val temporaryFilesAfterRun = listTemporaryFiles()

        temporaryFilesAfterRun should have size temporaryFileCountBeforeRun
      }
    }
  }

  def withServer(setup: Setup)(block: TestServer => Unit): Unit = {
    ServiceTest.withServer(
      setup.configureBuilder((registerService _).asJava),
      new Procedure[TestServer] {
        override def apply(server: TestServer): Unit = block(server)
      }
    )
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

  override final def descriptor: Descriptor = named("test")

}

class TestServiceImpl @Inject() extends TestService

class TestServiceModule extends AbstractModule with ServiceGuiceSupport {
  override def configure(): Unit = bindService(classOf[TestService], classOf[TestServiceImpl])
}
