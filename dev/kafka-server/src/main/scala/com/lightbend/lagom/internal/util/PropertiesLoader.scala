/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.util

import java.io.{ File, FileInputStream }
import java.util.Properties

object PropertiesLoader {
  def from(file: String): Properties = {
    val properties = new Properties()
    // First check if the file is on the classpath
    val is = {
      getClass.getResourceAsStream(file) match {
        case null =>
          // Try and load it as a file
          val f = new File(file)
          if (f.isFile) {
            new FileInputStream(f)
          } else {
            throw new IllegalArgumentException(s"File $file not found as classpath resource or on the filesystem")
          }
        case found => found
      }
    }

    try {
      properties.load(is)
      properties
    } finally {
      is.close()
    }
  }
}
