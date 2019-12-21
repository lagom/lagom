/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.persistence

import java.lang.reflect.Modifier

import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown
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
  // taken from akka-testkit's AkkaSpec
  private def testNameFromCallStack(classToStartFrom: Class[_]): String = {

    def isAbstractClass(className: String): Boolean = {
      try {
        Modifier.isAbstract(Class.forName(className).getModifiers)
      } catch {
        case _: Throwable => false // yes catch everything, best effort check
      }
    }

    val startFrom = classToStartFrom.getName
    val filteredStack = Thread.currentThread.getStackTrace.iterator
      .map(_.getClassName)
      // drop until we find the first occurrence of classToStartFrom
      .dropWhile(!_.startsWith(startFrom))
      // then continue to the next entry after classToStartFrom that makes sense
      .dropWhile {
        case `startFrom`                            => true
        case str if str.startsWith(startFrom + "$") => true // lambdas inside startFrom etc
        case str if isAbstractClass(str)            => true
        case _                                      => false
      }

    if (filteredStack.isEmpty)
      throw new IllegalArgumentException(s"Couldn't find [${classToStartFrom.getName}] in call stack")

    // sanitize for actor system name
    scrubActorSystemName(filteredStack.next())
  }

  // taken from akka-testkit's AkkaSpec
  /**
   * Sanitize the `name` to be used as valid actor system name by
   * replacing invalid characters. `name` may for example be a fully qualified
   * class name and then the short class name will be used.
   */
  private def scrubActorSystemName(name: String): String = {
    name
      .replaceFirst("""^.*\.""", "")  // drop package name
      .replaceAll("""\$\$?\w+""", "") // drop scala anonymous functions/classes
      .replaceAll("[^a-zA-Z_0-9]", "_")
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

  def this(config: Config) = this(ActorSystemSpec.testNameFromCallStack(classOf[ActorSystemSpec]), config)

  def this(setup: ActorSystemSetup) =
    this(ActorSystem(ActorSystemSpec.testNameFromCallStack(classOf[ActorSystemSpec]), setup))

  def this() = this(ConfigFactory.empty())

  override def afterAll(): Unit = {
    shutdown()
    super.afterAll()
  }

  val log: LoggingAdapter                      = Logging(system, this.getClass)
  val coordinatedShutdown: CoordinatedShutdown = CoordinatedShutdown(system)

  // for ScalaTest === compare of Class objects
  implicit def classEqualityConstraint[A, B]: CanEqual[Class[A], Class[B]] =
    new CanEqual[Class[A], Class[B]] {
      def areEqual(a: Class[A], b: Class[B]) = a == b
    }
}
