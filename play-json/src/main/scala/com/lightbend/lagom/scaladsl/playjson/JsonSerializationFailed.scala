/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.playjson

import scala.collection.Seq

import play.api.libs.json.JsPath
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.JsonValidationError

class JsonSerializationFailed private[lagom] (
    message: String,
    errors: Seq[(JsPath, Seq[JsonValidationError])],
    json: JsValue
) extends RuntimeException {

  override def getMessage: String =
    s"$message\nerrors:\n${errors.map(errorToString).mkString("\t", "\n\t", "\n")}}\n${Json.prettyPrint(json)}"

  private def errorToString(t: (JsPath, Seq[JsonValidationError])) = t match {
    case (path, pathErrors) => s"$path: " + pathErrors.mkString(", ")
  }
}
