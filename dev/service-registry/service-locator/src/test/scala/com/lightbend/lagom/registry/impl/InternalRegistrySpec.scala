/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.registry.impl

import java.net.URI
import java.util
import java.util.Collections

import com.lightbend.lagom.internal.javadsl.registry.ServiceRegistryService
import com.lightbend.lagom.javadsl.api.ServiceAcl
import org.scalatest.FlatSpec
import org.scalatest.Matchers

class InternalRegistrySpec extends FlatSpec with Matchers {

  behavior.of("InternalRegistry")

  it should "add unmanaged services" in {
    val simpleUri  = new URI("http://localhost.com:123/pathABC")
    val simpleName = "my-service"
    val unmanaged: UnmanagedServices = UnmanagedServices(
      Map(simpleName -> ServiceRegistryService.of(simpleUri, Collections.emptyList[ServiceAcl]))
    )
    val registry            = InternalRegistry.build(unmanaged)
    val actual: Option[URI] = registry.lookup(simpleName, None)
    actual should be(Some(simpleUri))
  }

  it should "add service registry services" in {
    val simpleUri  = new URI("http://localhost.com:123/pathABC")
    val simpleName = "my-service"
    val srs        = new ServiceRegistryService(simpleUri)
    val registry   = new InternalRegistry(Map.empty)
    registry.register(simpleName, srs)
    val actual: Option[URI] = registry.lookup(simpleName, None)
    actual should be(Some(simpleUri))
  }

  it should "add find http service when using no protoName lookup" in {
    val httpUri    = new URI("http://localhost.com:123/pathABC")
    val httpsUri   = new URI("https://localhost.com:123/pathABC")
    val simpleName = "my-service"
    val srs        = ServiceRegistryService.of(util.Arrays.asList(httpUri, httpsUri), Collections.emptyList[ServiceAcl])
    val registry   = new InternalRegistry(Map.empty)
    registry.register(simpleName, srs)
    val actual: Option[URI] = registry.lookup(simpleName, Some("http"))
    actual should be(Some(httpUri))
  }

  it should "add find http service when using `http` as protoName in lookup" in {
    val httpUri    = new URI("http://localhost.com:123/pathABC")
    val httpsUri   = new URI("https://localhost.com:123/pathABC")
    val simpleName = "my-service"
    val srs        = ServiceRegistryService.of(util.Arrays.asList(httpUri, httpsUri), Collections.emptyList[ServiceAcl])
    val registry   = new InternalRegistry(Map.empty)
    registry.register(simpleName, srs)
    val actual: Option[URI] = registry.lookup(simpleName, Some("http"))
    actual should be(Some(httpUri))
  }

  it should "add find https service when using `https` as protoName in lookup" in {
    val httpUri    = new URI("http://localhost.com:123/pathABC")
    val httpsUri   = new URI("https://localhost.com:123/pathABC")
    val simpleName = "my-service"
    val srs        = ServiceRegistryService.of(util.Arrays.asList(httpUri, httpsUri), Collections.emptyList[ServiceAcl])
    val registry   = new InternalRegistry(Map.empty)
    registry.register(simpleName, srs)
    val actual: Option[URI] = registry.lookup(simpleName, Some("https"))
    actual should be(Some(httpsUri))
  }

  it should "list all endpoints" in {
    val httpUri    = new URI("http://localhost.com:123/pathABC")
    val httpsUri   = new URI("https://localhost.com:123/pathABC")
    val simpleName = "my-service"
    val srs        = ServiceRegistryService.of(util.Arrays.asList(httpUri, httpsUri), Collections.emptyList[ServiceAcl])
    val registry   = new InternalRegistry(Map.empty)
    registry.register(simpleName, srs)
    val actual: Seq[(ServiceName, Option[String], URI)] = registry.list()
    actual.size should be(3)
    actual.map(_._2).toSet should be(Set(None, Some("https"), Some("http")))
  }

  it should "register services with `tcp://` scheme without a portName" in {
    val uri      = new URI("tcp://localhost:4000/cas_native")
    val registry = new InternalRegistry(Map.empty)
    registry.register("cas_native", new ServiceRegistryService(uri))

    val actual: Seq[(ServiceName, Option[String], URI)] = registry.list()
    actual.map(_._2).toSet should be(Set(None))
  }

}
