/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.registry.impl

import java.net.URI
import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.function.{ Function => JFunction }

import com.lightbend.lagom.javadsl.api.Descriptor.Call
import com.lightbend.lagom.javadsl.api.ServiceLocator

/**
 * An implementation of the service locator that always fails to locate the passed service's `name`.
 */
class NoServiceLocator extends ServiceLocator {
  import java.util.concurrent.CompletableFuture

  override def locate(name: String, serviceCall: Call[_, _]): CompletionStage[Optional[URI]] =
    CompletableFuture.completedFuture(Optional.empty())

  override def doWithService[T](
      name: String,
      serviceCall: Call[_, _],
      block: JFunction[URI, CompletionStage[T]]
  ): CompletionStage[Optional[T]] =
    CompletableFuture.completedFuture(Optional.empty())
}
