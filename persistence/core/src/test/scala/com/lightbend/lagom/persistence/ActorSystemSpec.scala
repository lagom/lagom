/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.persistence

import akka.actor.ActorSystem
import akka.actor.setup.ActorSystemSetup
import akka.event.Logging
import akka.event.LoggingAdapter
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalactic.CanEqual
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.WordSpecLike

object ActorSystemSpec {
  def getCallerName(clazz: Class[_]): String = {
    val s = Thread.currentThread.getStackTrace
      .map(_.getClassName)
      .drop(1)
      .dropWhile(_.matches("(java.lang.Thread|.*ActorSystemSpec.?$)"))
    val reduced = s.lastIndexWhere(_ == clazz.getName) match {
      case -1 ⇒ s
      case z  ⇒ s.drop(z + 1)
    }
    reduced.head.replaceFirst(""".*\.""", "").replaceAll("[^a-zA-Z_0-9]", "_")
  }

}

abstract class ActorSystemSpec(system: ActorSystem)
    extends TestKit(system)
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with TypeCheckedTripleEquals
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
  implicit def classEqualityConstraint[A, B]: CanEqual[Class[A], Class[B]] =
    new CanEqual[Class[A], Class[B]] {
      def areEqual(a: Class[A], b: Class[B]) = a == b
    }

}
