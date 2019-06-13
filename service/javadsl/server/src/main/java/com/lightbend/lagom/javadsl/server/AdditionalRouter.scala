/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.server

import java.util

import play.api.inject.Injector
import play.api.routing.Router

import scala.collection.JavaConverters._
import scala.collection.immutable

trait AdditionalRouter {
  def prefix: Option[String]
  def withPrefix(path: String): AdditionalRouter
}

final case class ClassBased[R <: Router](classType: Class[R], prefix: Option[String]) extends AdditionalRouter {
  def this(classType: Class[R]) = this(classType, None)

  def withPrefix(path: String): ClassBased[R] = copy(prefix = AdditionalRouter.appendPrefix(prefix, path))
}

final case class InstanceBased(router: Router, prefix: Option[String]) extends AdditionalRouter {
  def this(classType: Router) = this(classType, None)

  def withPrefix(path: String): InstanceBased = copy(prefix = AdditionalRouter.appendPrefix(prefix, path))
}

object AdditionalRouter {
  /**
   * Simulate Play's withPrefix behavior by prepending new prefix to an existing path.
   */
  private[lagom] def appendPrefix(prefix: Option[String], newPrefix: String) = {
    prefix
      .map(oldPrefix => newPrefix + "/" + oldPrefix)
      .orElse(Option(newPrefix))
  }
  private[lagom] def wireRouters(
      injector: Injector,
      additionalRouters: util.List[AdditionalRouter]
  ): util.List[Router] = {
    // modifies the Router in case a prefix is defined
    // otherwise returns the router as is
    def applyPrefix(router: Router, prefix: Option[String]): Router =
      prefix.map(router.withPrefix).getOrElse(router)

    additionalRouters.asScala
      .foldLeft(immutable.Seq.empty[Router]) { (routers, ar) =>
        ar match {
          case ar: InstanceBased =>
            routers :+ applyPrefix(ar.router, ar.prefix)

          case ar: ClassBased[_] =>
            val ins = injector.instanceOf(ar.classType)
            routers :+ applyPrefix(ins, ar.prefix)
        }
      }
      .asJava
  }
}
