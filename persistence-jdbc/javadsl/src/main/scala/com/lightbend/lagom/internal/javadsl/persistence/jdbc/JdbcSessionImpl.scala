/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.jdbc

import java.util.concurrent.CompletionStage
import javax.inject.{ Inject, Singleton }

import com.lightbend.lagom.javadsl.persistence.jdbc.JdbcSession
import com.lightbend.lagom.javadsl.persistence.jdbc.JdbcSession.ConnectionFunction

import scala.compat.java8.FutureConverters._

/**
 * INTERNAL API
 */
@Singleton
final class JdbcSessionImpl @Inject() (slick: SlickProvider) extends JdbcSession {

  import slick.profile.api._

  override def withConnection[T](block: ConnectionFunction[T]): CompletionStage[T] = {
    slick.db.run {
      SimpleDBIO { ctx =>
        block(ctx.connection)
      }
    }.toJava
  }

  override def withTransaction[T](block: ConnectionFunction[T]): CompletionStage[T] = {
    slick.db.run {
      SimpleDBIO { ctx =>
        block(ctx.connection)
      }.transactionally
    }.toJava
  }

}
