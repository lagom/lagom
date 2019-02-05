/*
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.client

import akka.discovery.Lookup

/**
  * A copy of Lookup object from Akka.
  * This implementation is less restrictive with respect to the service name part in SRV string.
  * see https://github.com/akka/akka/pull/26332
  *
  * This can be removed and replaced by the Akka one when it gets merged and released.
  *
  */
private[lagom] object LookupBuilder {

  /**
    * Create a service Lookup with only a serviceName.
    * Use withPortName and withProtocol to provide optional portName
    * and protocol
    */
  def apply(serviceName: String): Lookup = new Lookup(serviceName, None, None)

  /**
    * Create a service Lookup with `serviceName`, optional `portName` and optional `protocol`.
    */
  def apply(serviceName: String, portName: Option[String], protocol: Option[String]): Lookup =
    new Lookup(serviceName, portName, protocol)

  /**
    * Java API
    *
    * Create a service Lookup with only a serviceName.
    * Use withPortName and withProtocol to provide optional portName
    * and protocol
    */
  def create(serviceName: String): Lookup = new Lookup(serviceName, None, None)

  private val SrvQuery = """^_(.+?)\._(.+?)\.(.+?)$""".r

  private val ServiceName = "^[^.]([A-Za-z0-9-]\\.{0,1})+[^-_.]$".r

  /**
    * Create a service Lookup from a string with format:
    * _portName._protocol.serviceName.
    * (as specified by https://www.ietf.org/rfc/rfc2782.txt)
    *
    * If the passed string conforms with this format, a SRV Lookup is returned.
    * The serviceName part must be a valid domain name.
    *
    * The string is parsed and dismembered to build a Lookup as following:
    * Lookup(serviceName).withPortName(portName).withProtocol(protocol)
    *
    *
    * @throws NullPointerException If the passed string is null
    * @throws IllegalArgumentException If the string doesn't not conform with the SRV format
    */
  def parseSrv(str: String): Lookup =
    str match {
      case SrvQuery(portName, protocol, serviceName) if validServiceName(serviceName) ⇒
        Lookup(serviceName).withPortName(portName).withProtocol(protocol)

      case null ⇒
        throw new NullPointerException("Unable to create Lookup from passed SRV string. Passed value is 'null'")
      case _ ⇒
        throw new IllegalArgumentException(s"Unable to create Lookup from passed SRV string, invalid format: $str")
    }

  /**
    * Returns true if passed string conforms with SRV format. Otherwise returns false.
    */
  def isValidSrv(srv: String): Boolean =
    srv match {
      case SrvQuery(_, _, serviceName) ⇒ validServiceName(serviceName)
      case _ ⇒ false
    }

  private def validServiceName(name: String): Boolean =
    ServiceName.pattern.asPredicate().test(name)

}
