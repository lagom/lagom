/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

import java.net.HttpURLConnection
import java.io.BufferedReader
import java.io.InputStreamReader

import sbt.IO
import sbt.File

object DevModeBuild {

  def waitForReloads(file: File, count: Int): Unit = {
    waitFor[Int](
      IO.readLines(file).count(_.nonEmpty),
      _ == count,
      actual => s"Expected $count reloads, but only got $actual"
    )
  }

  def waitFor[T](check: => T, assertion: T => Boolean, error: T => String): Unit = {
    var checks = 0
    var actual = check
    while (!assertion(actual) && checks < 10) {
      Thread.sleep(1000)
      actual = check
      checks += 1
    }
    if (!assertion(actual)) {
      throw new RuntimeException(error(actual))
    }
  }

}
