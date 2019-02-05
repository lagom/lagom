/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.client

import com.typesafe.config.ConfigFactory
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ServiceNameMapperSpec extends WordSpec with Matchers {

  private val defaultConfig = ConfigFactory.defaultReference().getConfig("lagom.akka.discovery")
  private val parser = new ServiceNameMapper(defaultConfig)
  private def createParser(config: String) =
    new ServiceNameMapper(ConfigFactory.parseString(config).withFallback(defaultConfig))

  "The ServiceNameParser" should {

    "parse an unqualified Kubernetes SRV lookup" in {
      val serviceLookup = parser.mapLookupQuery("_fooname._fooprotocol.myservice")
      val lookup = serviceLookup.lookup
      lookup.serviceName should be("myservice")
      lookup.portName should be(Some("fooname"))
      lookup.protocol should be(Some("fooprotocol"))
      serviceLookup.scheme should be(Some("tcp"))
    }

    "parse a fully qualified Kubernetes SRV lookup" in {
      val lookup = parser.mapLookupQuery("_fooname._fooprotocol.myservice.mynamespace.svc.cluster.local").lookup
      lookup.serviceName should be("myservice.mynamespace.svc.cluster.local")
      lookup.portName should be(Some("fooname"))
      lookup.protocol should be(Some("fooprotocol"))
    }

    "parse an unqualified Kubernetes service name" in {
      val serviceLookup = parser.mapLookupQuery("myservice")
      val lookup = serviceLookup.lookup
      lookup.serviceName should be("myservice")
      lookup.portName should be(Some("http"))
      lookup.protocol should be(Some("tcp"))
      serviceLookup.scheme should be(Some("http"))
    }

    "parse an fully qualified Kubernetes service name" in {
      val lookup = parser.mapLookupQuery("myservice.mynamespace.svc.cluster.local").lookup
      lookup.serviceName should be("myservice.mynamespace.svc.cluster.local")
      lookup.portName should be(Some("http"))
      lookup.protocol should be(Some("tcp"))
    }

    "not include default port name if not specified" in {
      val lookup = createParser("""defaults.port-name = "" """).mapLookupQuery("myservice").lookup
      lookup.serviceName should be("myservice")
      lookup.portName should be(None)
      lookup.protocol should be(Some("tcp"))
    }

    "not include default port protocol if not specified" in {
      val lookup = createParser("""defaults.port-protocol = "" """).mapLookupQuery("myservice").lookup
      lookup.serviceName should be("myservice")
      lookup.portName should be(Some("http"))
      lookup.protocol should be(None)
    }

    "not attempt to parse the string if regex isn't specified" in {
      val lookup = createParser("""service-lookup-regex = "" """)
        .mapLookupQuery("_fooname._fooprotocol.myservice")
        .lookup
      lookup.serviceName should be("_fooname._fooprotocol.myservice")
      lookup.portName should be(Some("http"))
      lookup.protocol should be(Some("tcp"))
    }

    "include a suffix if configured" in {
      createParser("service-name-suffix = .mynamespace.svc.cluster.local")
        .mapLookupQuery("myservice")
        .lookup
        .serviceName should be("myservice.mynamespace.svc.cluster.local")
    }

    "return a mapped service name" in {
      val lookup = createParser("service-name-mappings.myservice.service-name = mappedmyservice")
        .mapLookupQuery("myservice")
        .lookup
      lookup.serviceName should be("mappedmyservice")
      lookup.portName should be(Some("http"))
      lookup.protocol should be(Some("tcp"))
    }

    "return a mapped port name" in {
      val serviceLookup = createParser("service-name-mappings.myservice.port-name = remoting")
        .mapLookupQuery("myservice")
      val lookup = serviceLookup.lookup
      lookup.serviceName should be("myservice")
      lookup.portName should be(Some("remoting"))
      lookup.protocol should be(Some("tcp"))
      serviceLookup.scheme should be(Some("tcp"))
    }

    "return a mapped port protocol" in {
      val lookup = createParser("service-name-mappings.myservice.port-protocol = udp")
        .mapLookupQuery("myservice")
        .lookup
      lookup.serviceName should be("myservice")
      lookup.portName should be(Some("http"))
      lookup.protocol should be(Some("udp"))
    }

    "return a mapped scheme" in {
      val serviceLookup = createParser("service-name-mappings.myservice.scheme = foo")
        .mapLookupQuery("myservice")
      val lookup = serviceLookup.lookup
      lookup.serviceName should be("myservice")
      lookup.portName should be(Some("http"))
      lookup.protocol should be(Some("tcp"))
      serviceLookup.scheme should be(Some("foo"))
    }

  }

}
