/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
import scala.concurrent.ExecutionContext

// Hack to expose play.core.Execution.trampoline, which is package private to [play], to be package private to [lagom].
// We put a sealed trait (so nothing can create an instance of it outside of this file) in the play package which can
// access the trampoline context, and then the only implementation of that sealed trait is made package private to
// [lagom].
package play.lagom {
  sealed trait Execution {
    implicit def trampoline: ExecutionContext = play.core.Execution.trampoline
  }
}

package com.lightbend.lagom.internal.api {
  private[lagom] object Execution extends play.lagom.Execution
}

