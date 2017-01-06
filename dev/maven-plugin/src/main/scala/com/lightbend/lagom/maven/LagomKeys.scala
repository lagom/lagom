/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.maven

import org.apache.maven.project.MavenProject

/**
 * Keys for sharing state in the Maven context.
 *
 * It's important to understand the Maven classloader hierarchy. Each plugin in each project has its own classloader.
 * This means, if the same plugin is used by two different projects, that same plugin will be loaded in two different
 * classloaders, and so won't be able to share classes between the two instances of itself. So any state shared between
 * projects must either be reflectively invoked (eg using structural typing) or must use JDK and/or core maven classes.
 *
 * All that said, maven caches ClassLoaders, and if it's the same version of the plugin with same configured
 * dependencies/extensions, and the project doesn't add any special extensions to the build, etc, then the same
 * classloader will be used. This is the typical case, which means we don't have to worry about the performance
 * implications of the Lagom plugin (as well as Scala) being loaded many times. But, it does mean we have to be
 * careful not to share classes, because it usually will work, so it won't always be apparent that we're doing it
 * wrong.
 */
object LagomKeys {
  /** Whether this project is a lagom service */
  val LagomService = new LagomKey[Boolean]("lagomService")

  /** Whether this project is a Play service */
  val PlayService = new LagomKey[Boolean]("playService")

  /** The URL of the service */
  val LagomServiceUrl = new LagomKey[String]("lagomServiceUrl")
}

final class LagomKey[A](name: String) {
  private val key = s"com.lightbend.lagom.maven.LagomKeys.$name"
  def get(project: MavenProject): Option[A] = {
    Option(project.getContextValue(key).asInstanceOf[A])
  }
  def put(project: MavenProject, a: A): Unit = {
    project.setContextValue(key, a)
  }
  def remove(project: MavenProject): Unit = {
    project.setContextValue(key, null)
  }
  override def toString = name
}
