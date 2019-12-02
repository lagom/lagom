/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.registry.impl

import java.net.URI
import java.util
import java.util.Collections

import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistryService
import com.lightbend.lagom.javadsl.api.ServiceAcl
import com.lightbend.lagom.javadsl.api.transport.Method
import com.lightbend.lagom.registry.impl.ServiceRegistryActor.Found
import com.lightbend.lagom.registry.impl.ServiceRegistryActor.Route
import org.scalatest.FlatSpec
import org.scalatest.FunSuite
import org.scalatest.Matchers

class InternalRouterSpec extends FlatSpec with Matchers {

  behavior.of("InternalRouter")

  it should "find the appropriate URI given the portName" in {

    val httpUri    = new URI("http://localhost.com/pathABC")
    val httpsUri   = new URI("https://localhost.com:123/pathABC")
    val simpleName = "my-service"
    val acl        = ServiceAcl.methodAndPath(Method.GET, "/pathABC")
    val srs        = ServiceRegistryService.of(util.Arrays.asList(httpUri, httpsUri), Collections.singletonList(acl))
    val registry   = new InternalRegistry(Map.empty)
    registry.register(simpleName, srs)
    val router = new InternalRouter

    router.rebuild(registry)

    router.routeFor(Route("GET", "/pathABC", None)) should be(Found(httpUri))
    router.routeFor(Route("GET", "/pathABC", Some("http"))) should be(Found(httpUri))
    router.routeFor(Route("GET", "/pathABC", Some("https"))) should be(Found(httpsUri))
  }

}
