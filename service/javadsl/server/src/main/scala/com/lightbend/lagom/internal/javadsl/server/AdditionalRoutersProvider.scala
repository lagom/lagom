/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.server

import java.util

import com.lightbend.lagom.javadsl.server.AdditionalRouters
import javax.inject.{ Inject, Provider }
import play.api.inject.Injector
import play.routing.Router
import scala.collection.immutable

class AdditionalRoutersProvider @Inject() (
  injector:          Injector,
  additionalRouters: util.List[AdditionalRouter]
) extends Provider[util.List[Router]] {

  override def get(): util.List[Router] =
    AdditionalRouter.wireRouters(injector, additionalRouters)
}
