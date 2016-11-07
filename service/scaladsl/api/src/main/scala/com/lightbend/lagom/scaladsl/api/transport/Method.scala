/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api.transport

final class Method(val name: String) extends AnyVal {
  override def toString: String = name
}

object Method {
  val GET = new Method("GET")
  val POST = new Method("POST")
  val PUT = new Method("PUT")
  val DELETE = new Method("DELETE")
  val HEAD = new Method("HEAD")
  val OPTIONS = new Method("OPTIONS")
  val PATCH = new Method("PATCH")
}
