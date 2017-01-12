/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.maven

import java.net.URLClassLoader
import javax.inject.{ Inject, Singleton }

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
    "org.scala-lang" -> "scala-library",
    "org.scala-lang" -> "scala-reflect",
    "org.scala-lang.modules" -> "scala-xml",
    "org.scala-lang.modules" -> "scala-parser-combinators",
    "org.scala-lang.modules" -> "scala-java8-compat"
  )

  private val ScalaVersionPattern = "_\\d+\\.\\d+.*$".r
  private def stripScalaVersion(artifactId: String) = ScalaVersionPattern.replaceFirstIn(artifactId, "")

  private def createCacheKey(artifacts: Seq[Artifact]): String = {
    artifacts.map { artifact =>
      import artifact._
      s"$getGroupId:$getArtifactId:$getVersion"
    }.sorted.mkString(",")
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
        val classLoader = new URLClassLoader(scalaArtifacts.map(_.getFile.toURI.toURL).toArray, null)
        cache += (cacheKey -> classLoader)
        classLoader
    }
  }

}
