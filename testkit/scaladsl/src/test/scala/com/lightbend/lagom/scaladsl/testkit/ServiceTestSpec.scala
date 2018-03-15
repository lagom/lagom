/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.testkit

import java.nio.file.{ Files, Path, Paths }

import akka.{ Done, NotUsed }
import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service, ServiceCall }
import com.lightbend.lagom.scaladsl.server._
import org.scalatest.{ Matchers, WordSpec }
import play.api.libs.ws.ahc.AhcWSComponents

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.Properties

class ServiceTestSpec extends WordSpec with Matchers {
  "ServiceTest" when {
    "started" should {
      "create a temporary directory" in {
        val temporaryFileCountBeforeRun = listTemporaryFiles().size

        ServiceTest.withServer(ServiceTest.defaultSetup.withCassandra())(new TestApplication(_)) { _ =>
          val temporaryFilesDuringRun = listTemporaryFiles()

          temporaryFilesDuringRun should have size (temporaryFileCountBeforeRun + 1)
        }
      }
    }

    "stopped after starting" should {
      "remove its temporary directory" in {
        val temporaryFileCountBeforeRun = listTemporaryFiles().size

        ServiceTest.withServer(ServiceTest.defaultSetup.withCassandra())(new TestApplication(_)) { _ => () }

        val temporaryFilesAfterRun = listTemporaryFiles()

        temporaryFilesAfterRun should have size temporaryFileCountBeforeRun
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

  override final def descriptor: Descriptor = named("test")

}

class TestServiceImpl extends TestService

class TestApplication(context: LagomApplicationContext) extends LagomApplication(context)
  with LocalServiceLocator
  with AhcWSComponents {

  override lazy val lagomServer: LagomServer = serverFor[TestService](new TestServiceImpl)

}
