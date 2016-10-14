/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.playjson

import play.api.libs.json._

import scala.collection.immutable
import scala.reflect.ClassTag

/**
 * Conveneince factories to create [[Migration]]s.
 */
object Migrations {

  def apply(
    currentVersion:          Int,
    transformation:          (Int, JsObject) => JsObject,
    classNameTransformation: (Int, String) => String
  ): Migration =
    new Migration(currentVersion) {
      override def transform(fromVersion: Int, json: JsObject): JsObject = transformation(fromVersion, json)
      override def transformClassName(fromVersion: Int, className: String): String = classNameTransformation(fromVersion, className)
    }

  /**
   * @param currentVersion The current version of the entity
   * @param transformations A set of changes applied incrementally if an old version of the serialized object is found
   */
  def transform[T: ClassTag](currentVersion: Int, transformations: immutable.SortedMap[Int, Reads[JsObject]]): (String, Migration) = {
    require(
      currentVersion > transformations.keys.last,
      s"currentVersion $currentVersion is not higher than the last transformation version ${transformations.keys.last}"
    )
    val className = implicitly[ClassTag[T]].runtimeClass.getName
    className -> new Migration(currentVersion) {
      override def transform(fromVersion: Int, json: JsObject): JsObject = {
        val keyIterator = transformations.keysIteratorFrom(fromVersion)
        // apply each transformation from the stored version up to current
        keyIterator.foldLeft(json) { (json, key) =>
          val transformation = transformations(key)
          transformation.reads(json) match {
            case JsSuccess(transformed, _) =>
              transformed
            case JsError(errors) =>
              throw new RuntimeException(
                s"Failed to transform json from [$className] in old version $fromVersion, at migration step $key, " +
                  s"Errors: ${errors.mkString(", ")}\n" +
                  s"json:\n ${Json.prettyPrint(json)}"
              )
          }
        }
      }
    }
  }

  def renamed(fromClassName: String, inVersion: Int, toClass: Class[_]): (String, Migration) =
    renamed(fromClassName, inVersion, toClass.getName)

  def renamed(fromClassName: String, inVersion: Int, toClassName: String): (String, Migration) =
    fromClassName -> new Migration(inVersion) {
      override def transformClassName(fromVersion: Int, className: String): String = toClassName
    }
}

/**
 * Data migration of old formats to current format can
 * be implemented in a concrete subclass or provided through the
 * factories in [[Migrations]] and configured to be used by the
 * `PlayJsonSerializer` for a changed class.
 *
 * It is used when deserializing data of older version than the
 * [[currentVersion]]. You implement the transformation of the
 * JSON structure in the [[transform]] method. If you have changed the
 * class name you should override [[transformClassName]] and return
 * current class name.
 */
abstract class Migration(val currentVersion: Int) {

  /**
   * Override to provide transformation of the old JSON structure to the new
   * JSON structure.
   *
   * @param fromVersion the version of the old data
   * @param json the old JSON data
   */
  def transform(fromVersion: Int, json: JsObject): JsObject = json

  /**
   * Override this method if you have changed the class name. Return
   * current class name.
   */
  def transformClassName(fromVersion: Int, className: String): String = className

}
