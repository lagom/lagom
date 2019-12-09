/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.api

import org.scalatest.Inside
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ScalaSupportSpec extends AnyWordSpec with Matchers with Inside {
  "scala support" should {
    "resolve a function" in {
      val method: ScalaServiceSupport.ScalaMethodCall[String] = testMethod _
      method.method.getDeclaringClass should ===(this.getClass)
      method.method.getName should ===("testMethod")
    }
  }

  def testMethod(s: String): String = s
}
