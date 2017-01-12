/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.api

import scala.util.matching.Regex

// https://github.com/retronym/scalac-survival-guide/blob/master/src/main/scala/guide/_19_ScalaSig.scala
// Jason warned me it may not be robust, but it seems to work fine for the specific purpose we have (i.e., 
// checking if a top-level Class was created with Scala).
object ScalaSig {
  private val ModuleClassName: Regex = """(.*)\$""".r
  private val ImplClassName: Regex = """(.*)\$class""".r

  def isScala(cls: Class[_]) = {
    import scala.reflect.{ ScalaLongSignature, ScalaSignature }
    def hasAnn(cls: Class[_]): Boolean = {
      val anns = List(classOf[ScalaSignature], classOf[ScalaLongSignature])
      anns.exists(ann => cls.getDeclaredAnnotation(ann) != null)
    }
    def classForName(name: String, init: Boolean, loader: ClassLoader): Option[Class[_]] = try {
      Some(Class.forName(name, init, loader))
    } catch {
      case _: ClassNotFoundException =>
        None
    }

    def topLevelClass(cls: Class[_]): Class[_] = {
      if (cls.getEnclosingClass != null) topLevelClass(cls.getEnclosingClass)
      else {
        cls.getName match {
          case ModuleClassName(companionClassName) =>
            classForName(companionClassName, init = false, cls.getClassLoader).getOrElse(cls)
          case ImplClassName(interfaceName) =>
            classForName(interfaceName, init = false, cls.getClassLoader).getOrElse(cls)
          case _ => cls
        }
      }
    }
    hasAnn(topLevelClass(cls))
  }
}
