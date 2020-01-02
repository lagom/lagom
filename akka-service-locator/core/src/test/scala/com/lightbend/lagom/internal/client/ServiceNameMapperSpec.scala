/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.client

import akka.discovery.Lookup
import com.typesafe.config.ConfigFactory
import org.scalatest.Matchers
import org.scalatest.WordSpec

class ServiceNameMapperSpec extends WordSpec with Matchers {
  private val defaultConfig = ConfigFactory.defaultReference().getConfig("lagom.akka.discovery")
  private val parser        = new ServiceNameMapper(defaultConfig)

  val defaultPortName = Some("http")
  val defaultProtocol = Some("tcp")
  val defaultScheme   = Some("http")

  private def createParser(config: String) =
    new ServiceNameMapper(ConfigFactory.parseString(config).withFallback(defaultConfig))

  "The ServiceNameMapper" should {
    // ------------------------------------------------------------------------------
    // Assert SRV lookups
    "parse an unqualified SRV lookup" in {
      val lookup = parser.mapLookupQuery("_fooname._fooprotocol.myservice").lookup
      lookup shouldBe Lookup("myservice", Some("fooname"), Some("fooprotocol"))
    }

    "parse a fully-qualified SRV lookup" in {
      val lookup = parser.mapLookupQuery("_fooname._fooprotocol.myservice.mynamespace.svc.cluster.local").lookup
      lookup shouldBe Lookup("myservice.mynamespace.svc.cluster.local", Some("fooname"), Some("fooprotocol"))
    }

    // ------------------------------------------------------------------------------
    // Assert simple Service Name lookup
    "parse an unqualified service name" in {
      val lookup = parser.mapLookupQuery("myservice").lookup
      lookup shouldBe Lookup("myservice", defaultPortName, defaultProtocol)
    }

    "parse an fully-qualified service name" in {
      val lookup = parser.mapLookupQuery("myservice.mynamespace.svc.cluster.local").lookup
      lookup shouldBe Lookup("myservice.mynamespace.svc.cluster.local", defaultPortName, defaultProtocol)
    }

    // ------------------------------------------------------------------------------
    // Assert blank defaults
    "not include default port name if defaults.port-name is blank" in {
      val customParser = createParser("""defaults.port-name = "" """)
      val lookup       = customParser.mapLookupQuery("myservice").lookup
      lookup shouldBe Lookup("myservice", None, defaultProtocol)
    }

    "not include default port name if defaults.port-name is null" in {
      val customParser = createParser("""defaults.port-name = null """)
      val lookup       = customParser.mapLookupQuery("myservice").lookup
      lookup shouldBe Lookup("myservice", None, defaultProtocol)
    }

    "not include default port protocol if defaults.port-protocol is blank" in {
      val customParser = createParser("""defaults.port-protocol = "" """)
      val lookup       = customParser.mapLookupQuery("myservice").lookup
      lookup shouldBe Lookup("myservice", defaultPortName, None)
    }

    "not include default port protocol if defaults.port-protocol is null" in {
      val customParser = createParser("""defaults.port-protocol = null """)
      val lookup       = customParser.mapLookupQuery("myservice").lookup
      lookup shouldBe Lookup("myservice", defaultPortName, None)
    }

    // ------------------------------------------------------------------------------
    // Assert custom mappings
    "return a mapped service name" in {
      val customParser = createParser("service-name-mappings.myservice.lookup = mappedmyservice")
      val lookup       = customParser.mapLookupQuery("myservice").lookup
      lookup shouldBe Lookup("mappedmyservice", defaultPortName, defaultProtocol)
    }

    "return a mapped port name" in {
      val customParser = createParser("service-name-mappings.myservice.lookup = _remoting._udp.mappedmyservice")
      val lookup       = customParser.mapLookupQuery("myservice").lookup
      lookup shouldBe Lookup("mappedmyservice", Some("remoting"), Some("udp"))
    }

    // ------------------------------------------------------------------------------
    // Assert Schema mapping
    "return a default scheme if not specified" in {
      val serviceLookup = parser.mapLookupQuery("myservice")
      serviceLookup.scheme should be(defaultScheme)
    }

    "not include default scheme if defaults.scheme is blank" in {
      val customParser  = createParser("""defaults.scheme = "" """)
      val serviceLookup = customParser.mapLookupQuery("myservice")
      serviceLookup.scheme should be(None)
    }

    "not include default scheme if defaults.scheme is null" in {
      val customParser  = createParser("""defaults.scheme = null """)
      val serviceLookup = customParser.mapLookupQuery("myservice")
      serviceLookup.scheme should be(None)
    }

    "not include scheme if service-name-mappings.myservice.scheme is blank" in {
      val customParser  = createParser("""
                                        |service-name-mappings.myservice {
                                        |  lookup = _remoting._udp.mappedmyservice
                                        |  scheme = ""
                                        |}
        """.stripMargin)
      val serviceLookup = customParser.mapLookupQuery("myservice")
      serviceLookup.scheme should be(None)
    }

    "not include scheme if service-name-mappings.myservice.scheme is null" in {
      val customParser  = createParser("""
                                        |service-name-mappings.myservice {
                                        |  lookup = _remoting._udp.mappedmyservice
                                        |  scheme = null
                                        |}
        """.stripMargin)
      val serviceLookup = customParser.mapLookupQuery("myservice")
      serviceLookup.scheme should be(None)
    }

    "return a mapped schema for a mapped SVR" in {
      val customParser  = createParser("""
                                        |service-name-mappings.myservice {
                                        |  lookup = _remoting._udp.mappedmyservice
                                        |  scheme = bar
                                        |}
        """.stripMargin)
      val serviceLookup = customParser.mapLookupQuery("myservice")
      serviceLookup.scheme should be(Some("bar"))
    }

    "return a default schema for a mapped SVR" in {
      val customParser  = createParser("""
                                        |service-name-mappings.myservice {
                                        |  lookup = _remoting._udp.mappedmyservice
                                        |}
        """.stripMargin)
      val serviceLookup = customParser.mapLookupQuery("myservice")
      serviceLookup.scheme should be(defaultScheme)
    }

    "return a mapped scheme" in {
      val customParser  = createParser("service-name-mappings.myservice.scheme = foo")
      val serviceLookup = customParser.mapLookupQuery("myservice")
      serviceLookup.scheme should be(Some("foo"))
    }
  }
}
