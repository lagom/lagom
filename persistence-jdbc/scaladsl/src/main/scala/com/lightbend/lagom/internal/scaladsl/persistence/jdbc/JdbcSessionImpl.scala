/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.scaladsl.persistence.jdbc

import java.sql.Connection
import javax.inject.{ Inject, Singleton }

import com.lightbend.lagom.internal.persistence.jdbc.SlickProvider
import com.lightbend.lagom.scaladsl.persistence.jdbc.JdbcSession

import scala.concurrent.Future

/**
 * INTERNAL API
 */
@Singleton
final class JdbcSessionImpl @Inject() (slick: SlickProvider) extends JdbcSession {

  import slick.profile.api._

  override def withConnection[T](block: Connection => T): Future[T] = {
    slick.db.run {
      SimpleDBIO { ctx =>
        block(ctx.connection)
      }
    }
  }

  override def withTransaction[T](block: Connection => T): Future[T] = {
    slick.db.run {
      SimpleDBIO { ctx =>
        block(ctx.connection)
      }.transactionally
    }
  }

}
