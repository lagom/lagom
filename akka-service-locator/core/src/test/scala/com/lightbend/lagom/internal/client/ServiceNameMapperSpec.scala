/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.client

import akka.discovery.Lookup
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ServiceNameMapperSpec extends AnyWordSpec with Matchers {
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
    "return a DNS A lookup if defaults.port-name and defaults.port-protocol are 'blank'" in {
      val customParser = createParser("""{
                                        |defaults.port-name = ""
                                        |defaults.port-protocol = ""
                                        |}
        """.stripMargin)
      val lookup       = customParser.mapLookupQuery("myservice").lookup
      lookup shouldBe Lookup("myservice", None, None)
    }

    "return a DNS A lookup if defaults.port-name and defaults.port-protocol are 'null'" in {
      val customParser = createParser("""{
                                        |defaults.port-name = null
                                        |defaults.port-protocol = null
                                        |}
        """.stripMargin)
      val lookup       = customParser.mapLookupQuery("myservice").lookup
      lookup shouldBe Lookup("myservice", None, None)
    }

    "not include default port name if defaults.port-name is 'blank'" in {
      val customParser = createParser("""defaults.port-name = "" """)
      val lookup       = customParser.mapLookupQuery("myservice").lookup
      lookup shouldBe Lookup("myservice", None, defaultProtocol)
    }

    "not include default port name if defaults.port-name is 'null'" in {
      val customParser = createParser("""defaults.port-name = null """)
      val lookup       = customParser.mapLookupQuery("myservice").lookup
      lookup shouldBe Lookup("myservice", None, defaultProtocol)
    }

    "not include default port protocol if defaults.port-protocol is 'blank'" in {
      val customParser = createParser("""defaults.port-protocol = "" """)
      val lookup       = customParser.mapLookupQuery("myservice").lookup
      lookup shouldBe Lookup("myservice", defaultPortName, None)
    }

    "not include default port protocol if defaults.port-protocol is 'null'" in {
      val customParser = createParser("""defaults.port-protocol = null """)
      val lookup       = customParser.mapLookupQuery("myservice").lookup
      lookup shouldBe Lookup("myservice", defaultPortName, None)
    }

    // ------------------------------------------------------------------------------
    // Assert custom mappings
    "return SRV lookup for a mapped service when defaults are not overwritten" in {
      val customParser = createParser("service-name-mappings.myservice.lookup = mappedmyservice")
      val lookup       = customParser.mapLookupQuery("myservice").lookup
      lookup shouldBe Lookup("mappedmyservice", defaultPortName, defaultProtocol)
    }

    "return a DNS A lookup for a mapped service when defaults are overwritten to 'null'" in {
      val customParser = createParser("""
                                        |service-name-mappings.myservice {
                                        |  lookup = mappedmyservice
                                        |  port-name = null
                                        |  port-protocol = null
                                        |}
        """.stripMargin)
      val lookup       = customParser.mapLookupQuery("myservice").lookup
      lookup shouldBe Lookup("mappedmyservice", None, None)
    }

    "return a DNS A lookup for a mapped service when defaults are overwritten to 'blank'" in {
      val customParser = createParser("""
                                        |service-name-mappings.myservice {
                                        |  lookup = mappedmyservice
                                        |  port-name = ""
                                        |  port-protocol = ""
                                        |}
        """.stripMargin)
      val lookup       = customParser.mapLookupQuery("myservice").lookup
      lookup shouldBe Lookup("mappedmyservice", None, None)
    }

    "return SRV lookup for a mapped service using SRV format" in {
      val customParser = createParser("service-name-mappings.myservice.lookup = _remoting._udp.mappedmyservice")
      val lookup       = customParser.mapLookupQuery("myservice").lookup
      lookup shouldBe Lookup("mappedmyservice", Some("remoting"), Some("udp"))
    }

    "honour SRV format in service name instead of overwritten port-name and port-protocol" in {
      val customParser = createParser("""
                                        |service-name-mappings.myservice {
                                        |  lookup = _remoting._udp.mappedmyservice
                                        |  port-name = some-port
                                        |  port-protocol = tcp
                                        |}
                                  """.stripMargin)
      val lookup       = customParser.mapLookupQuery("myservice").lookup
      lookup shouldBe Lookup("mappedmyservice", Some("remoting"), Some("udp"))
    }

    "return SRV lookup for a mapped service without configured 'lookup' field" in {
      val customParser = createParser("""
                                        |service-name-mappings.myservice {
                                        |  port-name = remoting
                                        |  port-protocol = udp
                                        |}
                                  """.stripMargin)
      val lookup       = customParser.mapLookupQuery("myservice").lookup
      lookup shouldBe Lookup("myservice", Some("remoting"), Some("udp"))
    }

    // ------------------------------------------------------------------------------
    // Assert Schema mapping
    "return a default scheme if not specified" in {
      val serviceLookup = parser.mapLookupQuery("myservice")
      serviceLookup.scheme should be(defaultScheme)
    }

    "not include default scheme if defaults.scheme is 'blank'" in {
      val customParser  = createParser("""defaults.scheme = "" """)
      val serviceLookup = customParser.mapLookupQuery("myservice")
      serviceLookup.scheme should be(None)
    }

    "not include default scheme if defaults.scheme is 'null'" in {
      val customParser  = createParser("""defaults.scheme = null """)
      val serviceLookup = customParser.mapLookupQuery("myservice")
      serviceLookup.scheme should be(None)
    }

    "not include scheme if service-name-mappings.myservice.scheme is 'blank'" in {
      val customParser  = createParser("""
                                        |service-name-mappings.myservice {
                                        |  lookup = _remoting._udp.mappedmyservice
                                        |  scheme = ""
                                        |}
        """.stripMargin)
      val serviceLookup = customParser.mapLookupQuery("myservice")
      serviceLookup.scheme should be(None)
    }

    "not include scheme if service-name-mappings.myservice.scheme is 'null'" in {
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
