/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.maven

import java.net.URLClassLoader
import javax.inject.Inject
import javax.inject.Singleton

import org.eclipse.aether.artifact.Artifact

/**
 * Implements sharing of Scala classloaders, to save on memory
 */
@Singleton
class ScalaClassLoaderManager @Inject() (logger: MavenLoggerProxy) {

  /**
   * The list of Scala libraries. None of these libraries may have a dependency outside of this list, otherwise there
   * will be classloading issues.
   *
   * Note that while adding more libraries to this list will allow more to be shared, it may also mean that classloaders
   * can be shared in less cases, since it becomes less likely that there will be an exact match between two projects
   * in what can be shared.
   */
  private val ScalaLibs = Set(
    "org.scala-lang"         -> "scala-library",
    "org.scala-lang"         -> "scala-reflect",
    "org.scala-lang.modules" -> "scala-xml",
    "org.scala-lang.modules" -> "scala-parser-combinators",
    "org.scala-lang.modules" -> "scala-java8-compat"
  )

  private val ScalaVersionPattern                   = "_\\d+\\.\\d+.*$".r
  private def stripScalaVersion(artifactId: String) = ScalaVersionPattern.replaceFirstIn(artifactId, "")

  private def createCacheKey(artifacts: Seq[Artifact]): String = {
    artifacts
      .map { artifact =>
        import artifact._
        s"$getGroupId:$getArtifactId:$getVersion"
      }
      .sorted
      .mkString(",")
  }

  private var cache = Map.empty[String, ClassLoader]

  /**
   * Extract a Scala ClassLoader from the given classpath.
   */
  def extractScalaClassLoader(artifacts: Seq[Artifact]): ClassLoader = synchronized {
    val scalaArtifacts = artifacts.filter { artifact =>
      ScalaLibs.contains(artifact.getGroupId -> stripScalaVersion(artifact.getArtifactId))
    }

    val cacheKey = createCacheKey(scalaArtifacts)
    cache.get(cacheKey) match {
      case Some(classLoader) =>
        logger.debug(s"ScalaClassLoader cache hit - $cacheKey")
        classLoader
      case None =>
        logger.debug(s"ScalaClassLoader cache miss - $cacheKey")
        // Use System classloader parent as documented here:
        // https://svn.apache.org/repos/infra/websites/production/maven/content/reference/maven-classloading.html#Maven_API_classloader
        // Keep in mind this does not contain any application or javaagent classes, which will
        // be added in the classLoader below.
        //
        // This behaves a little different depending on the Java version used:
        // - For Java 8: the parent is the boostrap class loader (or null), which in the end
        //   means the boostrap class loader is used.
        // - For Java9+: the parent is the platform class loader is a parent or an ancestor
        //   of the system class loader that all platform classes are visible to it.
        val parent      = ClassLoader.getSystemClassLoader().getParent()
        val classLoader = new URLClassLoader(scalaArtifacts.map(_.getFile.toURI.toURL).toArray, parent)
        cache += (cacheKey -> classLoader)
        classLoader
    }
  }
}
