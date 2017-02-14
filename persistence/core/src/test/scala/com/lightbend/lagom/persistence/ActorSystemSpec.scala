/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.persistence

import akka.actor.ActorSystem
import akka.actor.setup.ActorSystemSetup
import akka.event.{ Logging, LoggingAdapter }
import akka.testkit.{ ImplicitSender, TestKit }
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalactic.{ Constraint, ConversionCheckedTripleEquals }
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }

object ActorSystemSpec {
  def getCallerName(clazz: Class[_]): String = {
    val s = (Thread.currentThread.getStackTrace map (_.getClassName) drop 1)
      .dropWhile(_ matches "(java.lang.Thread|.*ActorSystemSpec.?$)")
    val reduced = s.lastIndexWhere(_ == clazz.getName) match {
      case -1 ⇒ s
      case z  ⇒ s drop (z + 1)
    }
    reduced.head.replaceFirst(""".*\.""", "").replaceAll("[^a-zA-Z_0-9]", "_")
  }

}

abstract class ActorSystemSpec(system: ActorSystem) extends TestKit(system)
  with WordSpecLike with Matchers with BeforeAndAfterAll with ConversionCheckedTripleEquals
  with ImplicitSender {

  def this(testName: String, config: Config) =
    this(ActorSystem(testName, config))

  def this(config: Config) = this(ActorSystemSpec.getCallerName(getClass), config)

  def this(setup: ActorSystemSetup) = this(ActorSystem(ActorSystemSpec.getCallerName(getClass), setup))

  def this() = this(ConfigFactory.empty())

  override def afterAll(): Unit = {
    shutdown()
    super.afterAll()
  }

  val log: LoggingAdapter = Logging(system, this.getClass)

  // for ScalaTest === compare of Class objects
  implicit def classEqualityConstraint[A, B]: Constraint[Class[A], Class[B]] =
    new Constraint[Class[A], Class[B]] {
      def areEqual(a: Class[A], b: Class[B]) = a == b
    }

}
