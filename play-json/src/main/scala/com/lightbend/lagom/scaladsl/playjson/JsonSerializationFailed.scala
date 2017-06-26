/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.playjson

import play.api.libs.json.{ JsPath, JsValue, Json, JsonValidationError }

class JsonSerializationFailed private[lagom] (message: String, errors: Seq[(JsPath, Seq[JsonValidationError])], json: JsValue) extends RuntimeException {

  override def getMessage: String =
    s"$message\nerrors:\n${errors.map(errorToString).mkString("\t", "\n\t", "\n")}}\n${Json.prettyPrint(json)}"

  private def errorToString(t: (JsPath, Seq[JsonValidationError])) = t match {
    case (path, pathErrors) => s"$path: " + pathErrors.mkString(", ")
  }
}
