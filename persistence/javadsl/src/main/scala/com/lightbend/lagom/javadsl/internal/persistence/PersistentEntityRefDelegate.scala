/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.internal.persistence

import com.lightbend.lagom.persistence.CorePersistentEntityRef
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef
import java.util.concurrent.CompletionStage
import scala.concurrent.duration.FiniteDuration
import com.lightbend.lagom.persistence.CorePersistentEntity
import javax.inject.Inject

class PersistentEntityRefDelegate[Command] @Inject() (persistentEntityRef: CorePersistentEntityRef[Command]) extends PersistentEntityRef[Command] {
  def ask[Reply, Cmd <: Command with CorePersistentEntity.ReplyType[Reply]](command: Cmd): CompletionStage[Reply] = {
    import scala.compat.java8.FutureConverters._
    persistentEntityRef.ask[Reply, Cmd](command).toJava.asInstanceOf[CompletionStage[Reply]]
  }

  def withAskTimeout(timeout: FiniteDuration): PersistentEntityRef[Command] =
    new PersistentEntityRefDelegate(persistentEntityRef.withAskTimeout(timeout))

}
