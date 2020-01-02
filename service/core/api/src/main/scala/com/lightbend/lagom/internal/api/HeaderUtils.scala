/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.api

import java.util.Locale

object HeaderUtils {

  /**
   * Normalize an HTTP header name.
   *
   * @param name the header name
   * @return the normalized header name
   */
  @inline
  def normalize(name: String): String = name.toLowerCase(Locale.ENGLISH)
}
