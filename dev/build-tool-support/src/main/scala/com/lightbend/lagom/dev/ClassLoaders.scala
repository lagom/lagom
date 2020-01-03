/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.dev

import java.lang.reflect.Method
import java.net.URL
import java.net.URLClassLoader
import java.util
import java.util.Collections

/**
 * A ClassLoader with a toString() that prints name/urls.
 */
class NamedURLClassLoader(name: String, urls: Array[URL], parent: ClassLoader) extends URLClassLoader(urls, parent) {
  override def toString: String = name + "{" + getURLs.map(_.toString).mkString(", ") + "}"
}

class DelegatingClassLoader(
    commonLoader: ClassLoader,
    sharedClasses: Set[String],
    buildLoader: ClassLoader,
    applicationClassLoader: () => Option[ClassLoader]
) extends ClassLoader(commonLoader) {

  lazy val findResourceMethod: Method = {
    val method = classOf[ClassLoader].getDeclaredMethod("findResource", classOf[String])
    method.setAccessible(true)
    method
  }

  lazy val findResourcesMethod: Method = {
    val method = classOf[ClassLoader].getDeclaredMethod("findResources", classOf[String])
    method.setAccessible(true)
    method
  }

  override def loadClass(name: String, resolve: Boolean): Class[_] = {
    if (sharedClasses(name)) {
      buildLoader.loadClass(name)
    } else {
      super.loadClass(name, resolve)
    }
  }

  override def getResource(name: String): URL = {
    applicationClassLoader()
      .flatMap(cl => Option(findResourceMethod.invoke(cl, name).asInstanceOf[URL]))
      .getOrElse(super.getResource(name))
  }

  override def getResources(name: String): util.Enumeration[URL] = {
    val appResources = applicationClassLoader().fold(new util.Vector[URL]().elements) { cl =>
      findResourcesMethod.invoke(cl, name).asInstanceOf[util.Enumeration[URL]]
    }
    val superResources = super.getResources(name)
    if (!superResources.hasMoreElements) {
      appResources
    } else if (!appResources.hasMoreElements) {
      superResources
    } else {
      val resources = new util.LinkedHashSet[URL]
      while (appResources.hasMoreElements) resources.add(appResources.nextElement())
      while (superResources.hasMoreElements) resources.add(superResources.nextElement())
      Collections.enumeration(resources)
    }
  }

  override def toString: String = "DelegatingClassLoader, using parent: " + getParent
}

/**
 * A ClassLoader that only uses resources from its parent.
 *
 * The reason we only pull resources from our parent classloader is that the Delegating ClassLoader already uses
 * this classloaders findResources method to locate the resources provided by this ClassLoader, and so our parent
 * will already be returning our resources. Only pulling from the parent ensures we don't duplicate this.
 */
class DelegatedResourcesClassLoader(name: String, urls: Array[URL], parent: ClassLoader)
    extends NamedURLClassLoader(name, urls, parent) {
  require(parent ne null)
  override def getResources(name: String): java.util.Enumeration[java.net.URL] = getParent.getResources(name)
}
