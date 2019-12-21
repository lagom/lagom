/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.persistence

import java.lang.reflect.Modifier

object PersistenceSpec {
  // taken from akka-testkit's AkkaSpec
  private[lagom] def testNameFromCallStack(classToStartFrom: Class[_]): String = {

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
