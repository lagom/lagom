/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.lightbend.lagom.internal.scaladsl.client.ScaladslServiceResolver
import com.lightbend.lagom.scaladsl.api.{ Service, ServiceAcl, ServiceCall }
import com.lightbend.lagom.scaladsl.api.deser.DefaultExceptionSerializer
import com.lightbend.lagom.scaladsl.api.transport.Method
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.Future

class ServiceAclResolverSpec extends WordSpec with Matchers {

  class SomeService extends Service {
    private def echo[A] = ServiceCall[A, A](Future.successful)
    def callString: ServiceCall[String, String] = echo
    def callStreamed: ServiceCall[Source[String, NotUsed], Source[String, NotUsed]] = echo
    def callNotUsed: ServiceCall[NotUsed, NotUsed] = echo
    def restCallString: ServiceCall[String, String] = echo
    def restCallStreamed: ServiceCall[Source[String, NotUsed], Source[String, NotUsed]] = echo
    def restCallNotUsed: ServiceCall[NotUsed, NotUsed] = echo
    def withAutoAclTrue: ServiceCall[String, String] = echo
    def withAutoAclFalse: ServiceCall[String, String] = echo

    override def descriptor = {
      import Service._

      named("some-service").withCalls(
        call(callString),
        call(callStreamed),
        call(callNotUsed),
        restCall(Method.PUT, "/restcallstring", restCallString),
        restCall(Method.PUT, "/restcallstreamed", restCallStreamed),
        restCall(Method.PUT, "/restcallnotused", restCallNotUsed),
        call(withAutoAclTrue).withAutoAcl(true),
        call(withAutoAclFalse).withAutoAcl(false)
      )
    }
  }

  val resolver = new ScaladslServiceResolver(DefaultExceptionSerializer.Unresolved)

  "ScaladslServiceResolver" when {
    "when auto acl is true" should {
      val acls = resolver.resolve(new SomeService().descriptor.withAutoAcl(true)).acls

      "default to POST for service calls with used request messages" in {
        acls should contain(ServiceAcl.forMethodAndPathRegex(Method.POST, "\\Q/callString\\E"))
      }

      "default to GET for streamed service calls" in {
        acls should contain(ServiceAcl.forMethodAndPathRegex(Method.GET, "\\Q/callStreamed\\E"))
      }

      "default to GET for service calls with not used request messages" in {
        acls should contain(ServiceAcl.forMethodAndPathRegex(Method.GET, "\\Q/callNotUsed\\E"))
      }

      "use the specified method and path for rest calls" in {
        acls should contain(ServiceAcl.forMethodAndPathRegex(Method.PUT, "\\Q/restcallstring\\E"))
      }

      "use the specified method for rest calls when the request is streamed" in {
        acls should contain(ServiceAcl.forMethodAndPathRegex(Method.PUT, "\\Q/restcallstreamed\\E"))
      }

      "use the specified method and path for rest calls even when the request is unused" in {
        acls should contain(ServiceAcl.forMethodAndPathRegex(Method.PUT, "\\Q/restcallnotused\\E"))
      }

      "create an acl when an individual method has auto acl set to true" in {
        acls should contain(ServiceAcl.forMethodAndPathRegex(Method.POST, "\\Q/withAutoAclTrue\\E"))
      }

      "not create an acl when an individual method has auto acl set to false" in {
        acls should not contain ServiceAcl.forMethodAndPathRegex(Method.POST, "\\Q/withAutoAclFalse\\E")
      }

      "generate the right number of acls" in {
        acls should have size 7
      }

    }

    "auto acl is false" should {
      val acls = resolver.resolve(new SomeService().descriptor.withAutoAcl(false)).acls

      "create an acl when an individual method has auto acl set to true" in {
        acls should contain only ServiceAcl.forMethodAndPathRegex(Method.POST, "\\Q/withAutoAclTrue\\E")
      }
    }
  }

}
