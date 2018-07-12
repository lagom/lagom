/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.server
import java.util
import java.util.Collections

import play.routing.Router

trait AdditionalRouters {
  def getRouters: util.List[Router]
}

class EmptyAdditionalRouters extends AdditionalRouters {
  override def getRouters: util.List[Router] = Collections.emptyList()
}
class NonEmptyAdditionalRouters(val routers: util.List[Router]) extends AdditionalRouters {
  override def getRouters: util.List[Router] = routers
}
