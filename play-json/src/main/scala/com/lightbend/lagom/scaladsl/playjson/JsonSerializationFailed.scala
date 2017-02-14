/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.playjson

import play.api.data.validation.ValidationError
import play.api.libs.json.{ JsPath, JsValue, Json }

class JsonSerializationFailed private[lagom] (message: String, errors: Seq[(JsPath, Seq[ValidationError])], json: JsValue) extends RuntimeException {

  override def getMessage: String =
    s"$message\nerrors:\n${errors.map(errorToString).mkString("\t", "\n\t", "\n")}}\n${Json.prettyPrint(json)}"

  private def errorToString(t: (JsPath, Seq[ValidationError])) = t match {
    case (path, pathErrors) => s"$path: " + pathErrors.mkString(", ")
  }
}
