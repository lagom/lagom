/**
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package lagom

object GitHub {

  def url(v: String): String = {
    val branch = if (v.endsWith("SNAPSHOT")) "master" else v
    "https://github.com/lagom/lagom/tree/" + branch
  }
}
