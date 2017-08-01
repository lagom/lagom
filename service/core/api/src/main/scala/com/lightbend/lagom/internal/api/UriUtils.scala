/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.api

import java.net.URI

object UriUtils {

  /**
   * Generate a comma separated String containing the host and port of passed URIs.
   */
  def hostAndPorts(uris: Seq[URI]): String =
    uris.map(hostAndPort).mkString(",")

  /** Extra host and port of a URI */
  private def hostAndPort(uri: URI) = s"${uri.getHost}:${uri.getPort}"
}
