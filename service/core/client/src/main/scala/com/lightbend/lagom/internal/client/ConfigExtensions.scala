/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.client

import java.util

import com.typesafe.config.{ Config, ConfigException }

object ConfigExtensions {

  /**
   * INTERNAL API
   *
   * Utility method to support automatic wrapping of a String value in a [[java.util.List[String]]]
   *
   * This method will return a [[java.util.List[String]]] if the passed key is a [[java.util.List[String]]] or if it's [[String]], in which
   * case it returns a single element [[java.util.List[String]]].
   *
   * @param config - a [[Config]] instance
   * @param key    - the key to lookup
   * @throws ConfigException.WrongType in case value is neither a [[String]] nor a [[java.util.List[String]]]
   * @return a [[java.util.List[String]]] containing one or more values for the passed key if key it is found, empty list otherwise.
   */
  def getStringList(config: Config, key: String): util.List[String] = {
    config.getAnyRef(key) match {
      case _: String => util.Arrays.asList(config.getString(key))
      case _         => config.getStringList(key)
    }
  }
}
