/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api.tools

import java.net.URL
import java.util

import com.lightbend.lagom.api.tools.tests.scaladsl._
import com.lightbend.lagom.internal.javadsl.server.JavadslServiceDiscovery
import com.lightbend.lagom.javadsl.api.{ Descriptor, Service }
import org.scalatest._
import play.api.libs.json.Json

class ServiceDetectorSpec extends WordSpec with Matchers with Inside {

  "The service detector" should {

    "resolve the service descriptor for a LagomJava project with ACLs" in {
      val expectedJsonString =
        """
          |[
          |  {
          |    "name": "/aclservice",
          |    "acls": [
          |      {
          |        "method": "GET",
          |        "pathPattern": "\\Q/mocks/\\E([^/]+)"
          |      },
          |      {
          |        "method": "POST",
          |        "pathPattern": "\\Q/mocks\\E"
          |      }
          |    ]
          |  }
          |]
        """.stripMargin

      val javaServiceDiscovery = "com.lightbend.lagom.internal.javadsl.server.JavadslServiceDiscovery"
      val decoratedCL = decorateWithConfig(this.getClass.getClassLoader, "application-acl.conf")
      val actualJsonString = ServiceDetector.services(decoratedCL, javaServiceDiscovery)
      Json.parse(actualJsonString) shouldBe Json.parse(expectedJsonString)
    }

    "resolve the service descriptor for a LagomJava project without ACLs" in {
      val expectedJsonString =
        """
          |[
          |  {
          |    "name": "/noaclservice",
          |    "acls": []
          |  }
          |]
        """.stripMargin

      val javaServiceDiscovery = "com.lightbend.lagom.internal.javadsl.server.JavadslServiceDiscovery"
      val decoratedCL = decorateWithConfig(this.getClass.getClassLoader, "application-noacl.conf")
      val actualJsonString = ServiceDetector.services(decoratedCL, javaServiceDiscovery)
      Json.parse(actualJsonString) shouldBe Json.parse(expectedJsonString)
    }

    def decorateWithConfig(classLoader: ClassLoader, desiredApplicationConf: String): ClassLoader = {
      new ClassLoader() {
        override def getResources(name: String): util.Enumeration[URL] = {
          if (name.equals("application.conf")) {
            classLoader.getResources(desiredApplicationConf)
          } else {
            classLoader.getResources(name)
          }
        }

        override def loadClass(name: String): Class[_] = classLoader.loadClass(name)

      }
    }

    "resolve the service descriptions for a LagomScala project using `describeService` (with ACLs)" in {
      val expectedJsonString =
        """
          |[
          |  {
          |    "name": "/aclservice",
          |    "acls": [
          |      {
          |        "method": "GET",
          |        "pathPattern": "\\Q/scala-mocks/\\E([^/]+)"
          |      },
          |      {
          |        "method": "POST",
          |        "pathPattern": "\\Q/scala-mocks\\E"
          |      }
          |    ]
          |  }
          |]
        """.stripMargin

      val actualJsonString = ServiceDetector.services(this.getClass.getClassLoader, classOf[AclServiceLoader].getName)
      Json.parse(actualJsonString) shouldBe Json.parse(expectedJsonString)
    }

    "resolve the service descriptions for a LagomScala project using the deprecated `describeServices` (with ACLs)" in {
      val expectedJsonString =
        """
          |[
          |  {
          |    "name": "/aclservice",
          |    "acls": [
          |      {
          |        "method": "GET",
          |        "pathPattern": "\\Q/scala-mocks/\\E([^/]+)"
          |      },
          |      {
          |        "method": "POST",
          |        "pathPattern": "\\Q/scala-mocks\\E"
          |      }
          |    ]
          |  }
          |]
        """.stripMargin

      val actualJsonString = ServiceDetector.services(this.getClass.getClassLoader, classOf[LegacyAclServiceLoader].getName)
      Json.parse(actualJsonString) shouldBe Json.parse(expectedJsonString)
    }

    "resolve the service descriptions for a LagomScala project using `describeService` (without ACLs)" in {
      val expectedJsonString =
        """
          |[
          |  {
          |    "name": "/noaclservice",
          |    "acls": []
          |  }
          |]
        """.stripMargin

      val actualJsonString = ServiceDetector.services(this.getClass.getClassLoader, classOf[NoAclServiceLoader].getName)
      Json.parse(actualJsonString) shouldBe Json.parse(expectedJsonString)
    }

    "resolve the service descriptions for a LagomScala project using `describeService` (service is not locatable)" in {
      val expectedJsonString = "[]"

      val actualJsonString = ServiceDetector.services(this.getClass.getClassLoader, classOf[UndescribedServiceLoader].getName)
      Json.parse(actualJsonString) shouldBe Json.parse(expectedJsonString)
    }

    "resolve the service descriptions for a LagomScala project using the deprecated `describeServices` (service is not locatable)" in {
      val expectedJsonString = "[]"

      val actualJsonString = ServiceDetector.services(this.getClass.getClassLoader, classOf[LegacyUndescribedServiceLoader].getName)
      Json.parse(actualJsonString) shouldBe Json.parse(expectedJsonString)
    }

    "resolve the service interface based on a service implementation" in {
      trait ServiceInterface extends Service {
        override def descriptor(): Descriptor = null
      }
      class ServiceImpl extends ServiceInterface

      new JavadslServiceDiscovery().serviceInterfaceResolver(classOf[ServiceImpl]) shouldBe Some(classOf[ServiceInterface])
    }

    "resolve the parent service interface that has implemented the descriptor method" in {
      trait ParentServiceInterface extends Service {
        override def descriptor(): Descriptor = null
      }
      trait ChildServiceInterface extends ParentServiceInterface
      class ServiceImpl extends ChildServiceInterface

      new JavadslServiceDiscovery().serviceInterfaceResolver(classOf[ServiceImpl]) shouldBe Some(classOf[ParentServiceInterface])
    }

    "resolve the child service interface that has implemented the descriptor method" in {
      trait ParentServiceInterface extends Service
      trait ChildServiceInterface extends ParentServiceInterface {
        override def descriptor(): Descriptor = null
      }
      class ServiceImpl extends ChildServiceInterface

      new JavadslServiceDiscovery().serviceInterfaceResolver(classOf[ServiceImpl]) shouldBe Some(classOf[ChildServiceInterface])
    }

    def minify(s: String) =
      s.replace(" ", "").replace("\n", "")
  }
}
