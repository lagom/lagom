/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.persistence

import scala.concurrent.duration._
import scala.concurrent.Future
import akka.actor.ActorRef
import java.io.NotSerializableException
import akka.actor.NoSerializationVerificationNeeded
import akka.actor.ActorSystem
import akka.util.Timeout
import akka.pattern.{ ask => akkaAsk }

/**
 * Commands are sent to a [[PersistentEntity]] using a
 * `PersistentEntityRef`. It is retrieved with [[PersistentEntityRegistry#refFor]].
 */
class CorePersistentEntityRef[Command](
  val entityId: String,
  region:       ActorRef,
  system:       ActorSystem,
  askTimeout:   FiniteDuration
)
  extends NoSerializationVerificationNeeded {

  implicit private val timeout = Timeout(askTimeout)

  /**
   * Send the `command` to the [[PersistentEntity]]. The returned
   * `Future` will be completed with the reply from the `PersistentEntity`.
   * The type of the reply is defined by the command (see [[PersistentEntity.ReplyType]]).
   *
   * The `Future` may also be completed with failure, sent by the `PersistentEntity`
   * or a `akka.pattern.AskTimeoutException` if there is no reply within a timeout.
   * The timeout can defined in configuration or overridden using [[#withAskTimeout]].
   */
  def ask[Reply, Cmd <: Command with CorePersistentEntity.ReplyType[Reply]](command: Cmd): Future[Reply] = {
    import system.dispatcher
    (region ? CommandEnvelope(entityId, command)).flatMap {
      case exc: Throwable =>
        // not using akka.actor.Status.Failure because it is using Java serialization
        Future.failed(exc)
      case result: Reply => Future.successful(result)
    }
  }

  /**
   * The timeout for [[#ask]]. The timeout is by default defined in configuration
   * but it can be adjusted for a specific `PersistentEntityRef` using this method.
   * Note that this returns a new `PersistentEntityRef` instance with the given timeout
   * (`PersistentEntityRef` is immutable).
   */
  def withAskTimeout(timeout: FiniteDuration): CorePersistentEntityRef[Command] =
    new CorePersistentEntityRef(entityId, region, system, askTimeout = timeout)

  //  Reasons for why we don't not support serialization of the PersistentEntityRef:
  //  - it will rarely be sent as a message itself, so providing a serializer will not help
  //  - it will be embedded in other messages and the only way we could support that
  //    transparently is to implement java serialization (readResolve, writeReplace)
  //    like ActorRef, but we don't want to encourage java serialization anyway
  //  - serializing/embedding the entityId String in other messages is simple
  //  - might be issues with the type `Command`?
  @throws(classOf[java.io.ObjectStreamException])
  protected def writeReplace(): AnyRef =
    throw new NotSerializableException(s"${getClass.getName} is not serializable. Send the entityId instead.")

  override def toString(): String = s"PersistentEntityRef($entityId)"
}
