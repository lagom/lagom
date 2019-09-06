/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.persistence

import org.scalatest.Canceled
import org.scalatest.Exceptional
import org.scalatest.Failed
import org.scalatest.Outcome
import org.scalatest.TestSuiteMixin
import org.scalatest.TestSuite

import scala.util.control.NonFatal

/**
 * This can be mixed into flaky tests so that we don't care if they fail on CI, but we
 * care if they failed when running locally.
 */
trait TolerateFailuresWhenRunningContinuousIntegration extends TestSuiteMixin { this: TestSuite =>

  // See https://docs.travis-ci.com/user/environment-variables/#default-environment-variables
  val isTravis: Boolean     = sys.env.get("TRAVIS").exists(_.toBoolean)
  val isTravisCron: Boolean = sys.env.get("TRAVIS_EVENT_TYPE").exists(_.equalsIgnoreCase("cron"))

  // See https://circleci.com/docs/2.0/env-vars/#built-in-environment-variables
  val isCircleCi: Boolean = sys.env.get("CIRCLECI").exists(_.toBoolean)

  val isContinuousIntegration: Boolean = isTravis || isCircleCi
  val isCronBuild: Boolean             = isTravisCron // TODO We don't have cron builds for CircleCI yet

  protected abstract override def withFixture(test: NoArgTest): Outcome = {
    try {
      super.withFixture(test) match {
        case f: Failed        => handleUnsuccessfulOutcome(f.exception)(f)
        case Exceptional(e)   => handleUnsuccessfulOutcome(e)(Exceptional(e))
        case outcome: Outcome => outcome
      }
    } catch {
      case NonFatal(e)  => handleUnsuccessfulOutcome(e)(Exceptional(e))
      case t: Throwable => Exceptional(t)
    }
  }

  private def handleUnsuccessfulOutcome(t: Throwable)(outcome: => Outcome): Outcome = {
    // Do not ignore failures for cron builds
    if (isContinuousIntegration && !isCronBuild) Canceled(s"Ignoring ${t} since it is running on CI")
    else outcome
  }
}
