/**
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package lagom

object GitHub {

  def url(v: String): String = {
    val branch = if (v.endsWith("SNAPSHOT")) "master" else v
    "https://github.com/lagom/lagom/tree/" + branch
  }
}
