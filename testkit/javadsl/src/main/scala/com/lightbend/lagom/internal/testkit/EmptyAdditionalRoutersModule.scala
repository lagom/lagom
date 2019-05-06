/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.testkit

import java.util
import java.util.Collections

import akka.annotation.InternalApi
import com.google.inject.Binder
import com.google.inject.Module
import com.google.inject.TypeLiteral
import com.lightbend.lagom.javadsl.testkit.ServiceTest
import play.api.routing.Router

/**
 * Provides an empty binding for [[util.List[Router]]]. Some of our tests make user of [[ServiceTest]]
 * without using [[com.lightbend.lagom.javadsl.server.ServiceGuiceSupport]]. For those tests, it's need to add
 * and extra binding of the list of Routers
 */
@InternalApi
object EmptyAdditionalRoutersModule extends Module {
  override def configure(binder: Binder): Unit =
    binder.bind(new TypeLiteral[util.List[Router]]() {}).toInstance(Collections.emptyList[Router])

  def instance(): EmptyAdditionalRoutersModule.type = EmptyAdditionalRoutersModule
}
