/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.api.deser

trait PathParamSerializer[Param] {

}

object PathParamSerializer {
  implicit val StringPathParamSerializer: PathParamSerializer[String] = new PathParamSerializer[String] {
    override def toString = "String"
  }
}
