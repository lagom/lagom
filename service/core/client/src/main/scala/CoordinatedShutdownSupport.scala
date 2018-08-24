/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

// Hack to expose play.api.libs.concurrent.CoordinatedShutdownSupport, which is package private to [play], to be package private to [lagom].
// We put a sealed trait (so nothing can create an instance of it outside of this file) in the play package which can
// access CoordinatedShutdownSupport, and then the only implementation of that sealed trait is made package private to
// [lagom].
package play.lagom {

  import akka.Done
  import akka.actor.{ ActorSystem, CoordinatedShutdown }

  import scala.concurrent.Future

  sealed trait CoordinatedShutdownSupport {
    private val underlying = play.api.libs.concurrent.CoordinatedShutdownSupport

    /** Delegates to [[play.api.libs.concurrent.CoordinatedShutdownSupport.asyncShutdown()]] */
    def asyncShutdown(actorSystem: ActorSystem, reason: CoordinatedShutdown.Reason): Future[Done] =
      underlying.asyncShutdown(actorSystem, reason)

    /** Delegates to [[play.api.libs.concurrent.CoordinatedShutdownSupport.syncShutdown()]] */
    def syncShutdown(actorSystem: ActorSystem, reason: CoordinatedShutdown.Reason): Unit =
      underlying.syncShutdown(actorSystem, reason)
  }

}
package com.lightbend.lagom.internal.api {
  private[lagom] object CoordinatedShutdownSupport extends play.lagom.CoordinatedShutdownSupport
}

