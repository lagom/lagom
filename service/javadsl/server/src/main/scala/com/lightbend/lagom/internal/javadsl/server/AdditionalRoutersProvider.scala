/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.javadsl.server

import java.util

import akka.annotation.InternalApi
import com.lightbend.lagom.javadsl.server.AdditionalRouter
import javax.inject.Inject
import javax.inject.Provider
import play.api.inject.Injector
import play.api.routing.Router

/**
 * Provides a list of [[play.routing.Router]]s built from a list of [[AdditionalRouter]]
 *
 * @param injector
 * @param additionalRouters
 */
@InternalApi
private[lagom] class AdditionalRoutersProvider @Inject() (
    injector: Injector,
    additionalRouters: util.List[AdditionalRouter]
) extends Provider[util.List[Router]] {
  override def get(): util.List[Router] =
    AdditionalRouter.wireRouters(injector, additionalRouters)
}
