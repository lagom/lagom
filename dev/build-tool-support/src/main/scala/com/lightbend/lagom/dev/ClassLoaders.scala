/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.dev

import java.net.{ URL, URLClassLoader }
import java.util

/**
 * A ClassLoader with a toString() that prints name/urls.
 */
class NamedURLClassLoader(name: String, urls: Array[URL], parent: ClassLoader) extends URLClassLoader(urls, parent) {
  override def toString = name + "{" + getURLs.map(_.toString).mkString(", ") + "}"
}

class DelegatingClassLoader(commonLoader: ClassLoader, sharedClasses: Set[String], buildLoader: ClassLoader,
    applicationClassLoader: () => Option[ClassLoader]) extends ClassLoader(commonLoader) {

  lazy val findResourceMethod = {
    val method = classOf[ClassLoader].getDeclaredMethod("findResource", classOf[String])
    method.setAccessible(true)
    method
  }

  lazy val findResourcesMethod = {
    val method = classOf[ClassLoader].getDeclaredMethod("findResources", classOf[String])
    method.setAccessible(true)
    method
  }

  override def loadClass(name: String, resolve: Boolean) = {
    if (sharedClasses(name)) {
      buildLoader.loadClass(name)
    } else {
      super.loadClass(name, resolve)
    }
  }

  override def getResource(name: String) = {
    applicationClassLoader()
      .flatMap(cl => Option(findResourceMethod.invoke(cl, name).asInstanceOf[URL]))
      .getOrElse(super.getResource(name))
  }

  override def getResources(name: String) = {
    val appResources = applicationClassLoader().fold(new util.Vector[URL]().elements) { cl =>
      findResourcesMethod.invoke(cl, name).asInstanceOf[util.Enumeration[URL]]
    }
    val superResources = super.getResources(name)
    val resources = new util.Vector[URL]()
    while (appResources.hasMoreElements) resources.add(appResources.nextElement())
    while (superResources.hasMoreElements) resources.add(superResources.nextElement())
    resources.elements()
  }

  override def toString = "DelegatingClassLoader, using parent: " + getParent
}
