/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.playjson

import play.api.libs.json._

import scala.collection.immutable
import scala.reflect.ClassTag

/**
 * Convenience factories to create [[JsonMigration]]s.
 */
object JsonMigrations {
  def apply(
      currentVersion: Int,
      transformation: (Int, JsValue) => JsValue,
      classNameTransformation: (Int, String) => String
  ): JsonMigration =
    apply(currentVersion, currentVersion, transformation, classNameTransformation)

  def apply(
      currentVersion: Int,
      supportedForwardVersion:Int,
      transformation: (Int, JsValue) => JsValue,
      classNameTransformation: (Int, String) => String
  ): JsonMigration =
    new JsonMigration(currentVersion, supportedForwardVersion) {
      override def transform(fromVersion: Int, json: JsObject): JsValue     = transformation(fromVersion, json)
      override def transformValue(fromVersion: Int, json: JsValue): JsValue = transformation(fromVersion, json)
      override def transformClassName(fromVersion: Int, className: String): String =
        classNameTransformation(fromVersion, className)
    }

  /**
   * @param transformations A set of changes applied incrementally if an older version of the serialized object is found
   *                        each entry is the version when the change was introduced and the json transformation (created
   *                        through the play-json transformation DSL)
   */
  def transform[T: ClassTag](transformations: immutable.SortedMap[Int, Reads[JsObject]]): (String, JsonMigration) = {
    val currentVersion= transformations.keys.last + 1
    transform(transformations, currentVersion, currentVersion)
  }
  def transform[T: ClassTag](transformations: immutable.SortedMap[Int, Reads[JsObject]], currentVersion:Int, supportedForwardVersion: Int): (String, JsonMigration) = {
    val className = implicitly[ClassTag[T]].runtimeClass.getName
    className -> new JsonMigration(currentVersion, supportedForwardVersion) {
      override def transform(fromVersion: Int, json: JsObject): JsObject = {
        val keyIterator = transformations.keysIteratorFrom(fromVersion)
        // apply each transformation from the stored version up to current
        keyIterator.foldLeft(json) { (json, key) =>
          val transformation = transformations(key)
          transformation.reads(json) match {
            case JsSuccess(transformed, _) =>
              transformed
            case JsError(errors) =>
              throw new JsonSerializationFailed(
                s"Failed to transform json from [$className] in old version $fromVersion, at migration step $key",
                errors,
                json
              )
          }
        }
      }
    }
  }

  def renamed(fromClassName: String, inVersion: Int, toClass: Class[_]): (String, JsonMigration) =
    renamed(fromClassName, inVersion, toClass.getName)

  def renamed(fromClassName: String, inVersion: Int, toClassName: String): (String, JsonMigration) =
    fromClassName -> new JsonMigration(inVersion) {
      override def transformClassName(fromVersion: Int, className: String): String = toClassName
    }
}

/**
 * Data migration of old formats to current format can
 * be implemented in a concrete subclass or provided through the
 * factories in [[JsonMigrations]] and configured to be used by the
 * `PlayJsonSerializer` for a changed class.
 *
 * It is used when deserializing data of older version than the
 * [[currentVersion]]. You implement the transformation of the
 * JSON structure in the [[transform]] method. If you have changed the
 * class name you should override [[transformClassName]] and return
 * current class name.
 */
abstract class JsonMigration(val currentVersion: Int, val supportedForwardVersion: Int) {

  def this(currentVersion: Int) = this(currentVersion, currentVersion)

  require(
    currentVersion <= supportedForwardVersion,
    """The "currentVersion" of a JacksonMigration must be less or equal to the "supportedForwardVersion".""")

  /**
   * Override to provide transformation of the old JSON structure to the new
   * JSON structure.
   *
   * @param fromVersion the version of the old data
   * @param json the old JSON data
   */
  def transform(fromVersion: Int, json: JsObject): JsValue = json

  /**
   * Override to provide transformation of the old JSON value to the new
   * JSON value
   *
   * @param fromVersion the version of the old data
   * @param json the old JSON data
   */
  def transformValue(fromVersion: Int, json: JsValue): JsValue = json

  /**
   * Override this method if you have changed the class name. Return
   * current class name.
   */
  def transformClassName(fromVersion: Int, className: String): String = className
}
