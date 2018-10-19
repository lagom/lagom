/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
 
 package com.lightbend.lagom.internal.client

import akka.discovery.Lookup

object ServiceNameParser {

  private val SrvQuery = """^_(.+?)\._(.+?)\.(.+?)$""".r

  def toLookupQuery(name: String): Lookup = {
    name match {
      case SrvQuery(portName, protocol, serviceName) =>
        Lookup(serviceName).withPortName(portName).withProtocol(protocol)
      case _ => Lookup(name)
    }


  }
}
