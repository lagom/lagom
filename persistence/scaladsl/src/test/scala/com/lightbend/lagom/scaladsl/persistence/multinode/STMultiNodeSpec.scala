/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.multinode

import akka.remote.testkit.MultiNodeSpecCallbacks
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.WordSpecLike

/**
 * Hooks up MultiNodeSpec with ScalaTest
 */
trait STMultiNodeSpec extends MultiNodeSpecCallbacks
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def beforeAll(): Unit = {
    super.beforeAll()
    multiNodeSpecBeforeAll()
  }

  override def afterAll(): Unit = {
    multiNodeSpecAfterAll()
    super.afterAll()
  }
}
