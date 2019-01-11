/*
 * Copyright (C) 2018-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.client

import com.typesafe.config.ConfigFactory
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ServiceNameParserSpec extends WordSpec with Matchers {

  private val defaultConfig = ConfigFactory.defaultReference().getConfig("lagom.akka.discovery")
  private val parser = new ServiceNameParser(defaultConfig)
  private def createParser(config: String) =
    new ServiceNameParser(ConfigFactory.parseString(config).withFallback(defaultConfig))

  "The ServiceNameParser" should {

    "parse an unqualified Kubernetes SRV lookup" in {
      val lookup = parser.parseLookupQuery("_fooname._fooprotocol.myservice")
      lookup.serviceName should be("myservice")
      lookup.portName should be(Some("fooname"))
      lookup.protocol should be(Some("fooprotocol"))
    }

    "parse a fully qualified Kubernetes SRV lookup" in {
      val lookup = parser.parseLookupQuery("_fooname._fooprotocol.myservice.mynamespace.svc.cluster.local")
      lookup.serviceName should be("myservice.mynamespace.svc.cluster.local")
      lookup.portName should be(Some("fooname"))
      lookup.protocol should be(Some("fooprotocol"))
    }

    "parse an unqualified Kubernetes service name" in {
      val lookup = parser.parseLookupQuery("myservice")
      lookup.serviceName should be("myservice")
      lookup.portName should be(Some("http"))
      lookup.protocol should be(Some("tcp"))
    }

    "parse an fully qualified Kubernetes service name" in {
      val lookup = parser.parseLookupQuery("myservice.mynamespace.svc.cluster.local")
      lookup.serviceName should be("myservice.mynamespace.svc.cluster.local")
      lookup.portName should be(Some("http"))
      lookup.protocol should be(Some("tcp"))
    }

    "not include default port name if not specified" in {
      val lookup = createParser("""defaults.port-name = "" """).parseLookupQuery("myservice")
      lookup.serviceName should be("myservice")
      lookup.portName should be(None)
      lookup.protocol should be(Some("tcp"))
    }

    "not include default port protocol if not specified" in {
      val lookup = createParser("""defaults.port-protocol = "" """).parseLookupQuery("myservice")
      lookup.serviceName should be("myservice")
      lookup.portName should be(Some("http"))
      lookup.protocol should be(None)
    }

    "not attempt to parse the string if regex isn't specified" in {
      val lookup = createParser("""service-lookup-regex = "" """)
        .parseLookupQuery("_fooname._fooprotocol.myservice")
      lookup.serviceName should be("_fooname._fooprotocol.myservice")
      lookup.portName should be(Some("http"))
      lookup.protocol should be(Some("tcp"))
    }

    "include a suffix if configured" in {
      createParser("service-name-suffix = .mynamespace.svc.cluster.local")
        .parseLookupQuery("myservice")
        .serviceName should be("myservice.mynamespace.svc.cluster.local")
    }
  }

}
