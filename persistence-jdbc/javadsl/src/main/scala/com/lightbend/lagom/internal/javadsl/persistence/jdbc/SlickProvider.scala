/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.jdbc

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import akka.actor.ActorSystem

import javax.inject.{ Inject, Singleton }
import play.api.db.DBApi
import slick.ast._

@Singleton
class SlickProvider @Inject() (
  system: ActorSystem,
  dbApi:  DBApi /* Ensures database is initialised before we start anything that needs it */ )(implicit ec: ExecutionContext)
  extends com.lightbend.lagom.internal.persistence.jdbc.SlickProvider(system, dbApi)(ec)
