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

  /**
    * Validates domain name:
    * (as defined in https://tools.ietf.org/html/rfc1034)
    *
    * - a label has 1 to 63 chars
    * - valid chars for a label are: a-z, A-Z, 0-9 and -
    * - a label can't start with a 'hyphen' (-)
    * - a label can't start with a 'digit' (0-9)
    * - a label can't end with a 'hyphen' (-)
    * - labels are separated by a 'dot' (.)
    *
    * Starts with a label:
    * Label Pattern: (?![0-9-])[A-Za-z0-9-]{1,63}(?<!-)
    *      (?![0-9-]) => negative look ahead, first char can't be hyphen (-) or digit (0-9)
    *      [A-Za-z0-9-]{1,63} => digits, letters and hyphen, from 1 to 63
    *      (?<!-) => negative look behind, last char can't be hyphen (-)
    *
    * A label can be followed by other labels:
    *    Pattern: (\.(?![0-9-])[A-Za-z0-9-]{1,63}(?<!-)))*
    *      . => separated by a . (dot)
    *      label pattern => (?![0-9-])[A-Za-z0-9-]{1,63}(?<!-)
    *      * => match zero or more times
    */
  private val DomainName = "^((?![0-9-])[A-Za-z0-9-]{1,63}(?<!-))((\\.(?![0-9-])[A-Za-z0-9-]{1,63}(?<!-)))*$".r

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
    * @throws NullPointerException If the passed string is null
    * @throws IllegalArgumentException If the string doesn't not conform with the SRV format
    */
  def parseSrv(str: String): Lookup =
    str match {
      case SrvQuery(portName, protocol, serviceName) if validDomainName(serviceName) ⇒
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
      case SrvQuery(_, _, serviceName) ⇒ validDomainName(serviceName)
      case _ ⇒ false
    }

  private def validDomainName(name: String): Boolean =
    DomainName.pattern.asPredicate().test(name)

}
