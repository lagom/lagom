/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.util

import java.util.Properties

object PropertiesLoader {
  def from(file: String): Properties = {
    val properties = new Properties()
    properties.load(getClass.getResourceAsStream(file))
    properties
  }
}
