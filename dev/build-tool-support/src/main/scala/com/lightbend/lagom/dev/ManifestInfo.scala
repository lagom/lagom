/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.dev

import java.io.IOException
import java.util
import java.util.Arrays
import java.util.jar.{ Attributes, Manifest }

import play.dev.filewatch.LoggerProxy

import scala.collection.immutable

// TODO: if we move it out to another project, we should reconsider how to do logging
final class ManifestInfo(val classLoader: ClassLoader, logger: LoggerProxy) {
  import ManifestInfo._

  /**
   * Versions of artifacts from known vendors.
   */
  val versions: Map[String, Version] = {

    var manifests = Map.empty[String, Version]

    try {
      val resources = classLoader.getResources("META-INF/MANIFEST.MF")
      while (resources.hasMoreElements) {
        val ios = resources.nextElement().openStream()
        try {
          val manifest = new Manifest(ios)
          val attributes = manifest.getMainAttributes
          val title = attributes.getValue(new Attributes.Name(ImplTitle)) match {
            case null ⇒ attributes.getValue(new Attributes.Name(BundleName))
            case t    ⇒ t
          }
          val version = attributes.getValue(new Attributes.Name(ImplVersion)) match {
            case null ⇒ attributes.getValue(new Attributes.Name(BundleVersion))
            case v    ⇒ v
          }
          val vendor = attributes.getValue(new Attributes.Name(ImplVendor)) match {
            case null ⇒ attributes.getValue(new Attributes.Name(BundleVendor))
            case v    ⇒ v
          }

          if (title != null
            && version != null
            && vendor != null
            && knownVendors(vendor)) {
            manifests = manifests.updated(title, new Version(version))
          }
        } finally {
          ios.close()
        }
      }
    } catch {
      case ioe: IOException ⇒
        logger.warn("Could not read manifest information.")
        ioe.printStackTrace()
    }
    manifests
  }

  /**
   * Verify that the version is the same for all given artifacts.
   * @throws IllegalArgumentException if detects mismatch for a given family. Error are accumulated and final exception
   *                                  contains all error messages.
   */
  def failOnVersionMismatch(projects: ProjectFamily*): Unit = {
    val errors = collectErrorOnVersionMismatch(projects: _*)
    if (errors.nonEmpty) {
      val msg = errors.mkString(", ")
      throw new IllegalArgumentException(msg)
    }
  }

  /**
   * Verify that the version is the same for all given artifacts and return eventual error messages.
   * This is particular useful when using it as runtime check.
   * Callers can decide what to do with the message, eg: log using it's own log framework.
   */
  def collectErrorOnVersionMismatch(projects: ProjectFamily*): Seq[String] =
    projects.flatMap { family =>
      val filteredVersions = versions.filterKeys(family.libraries.toSet)
      val values = filteredVersions.values.toSet
      if (values.size > 1) {
        val conflictingVersions = values.mkString(", ")
        val fullInfo = filteredVersions.map { case (k, v) ⇒ s"$k:$v" }.mkString(", ")
        val highestVersion = values.max
        val message = "Detected possible incompatible versions on the classpath. " +
          s"Please note that a given ${family.familyName} version MUST be the same across all modules of ${family.familyName} " +
          s"that you are using, e.g. if you use [$highestVersion] all other modules that are released together MUST be of the " +
          "same version. Make sure you're using a compatible set of libraries." +
          s"Possibly conflicting versions [$conflictingVersions] in libraries [$fullInfo]"
        Some(message)
      } else
        None
    }

}

object ManifestInfo {

  private val ImplTitle = "Implementation-Title"
  private val ImplVersion = "Implementation-Version"
  private val ImplVendor = "Implementation-Vendor-Id"

  private val BundleName = "Bundle-Name"
  private val BundleVersion = "Bundle-Version"
  private val BundleVendor = "Bundle-Vendor"

  // TODO: reconsider this if we want to make it more generic
  // instead we should collect the versions only when doing the check and ProjectFamily should define the vendor id
  private val knownVendors = Set(
    "com.typesafe.akka",
    "com.lightbend.akka",
    "Lightbend Inc.",
    "Lightbend",
    "com.lightbend.lagom",
    "com.typesafe.play"
  )

  /**
   * Comparable version information
   */
  final class Version(val version: String) extends Comparable[Version] {
    private val (numbers: Array[Int], rest: String) = {
      val numbers = new Array[Int](3)
      val segments: Array[String] = version.split("[.-]")
      var segmentPos = 0
      var numbersPos = 0
      while (numbersPos < 3) {
        if (segmentPos < segments.length) try {
          numbers(numbersPos) = segments(segmentPos).toInt
          segmentPos += 1
        } catch {
          case e: NumberFormatException ⇒
            // This means that we have a trailing part on the version string and
            // less than 3 numbers, so we assume that this is a "newer" version
            numbers(numbersPos) = Integer.MAX_VALUE
        }
        numbersPos += 1
      }

      val rest: String =
        if (segmentPos >= segments.length) ""
        else String.join("-", util.Arrays.asList(util.Arrays.copyOfRange(segments, segmentPos, segments.length): _*))

      (numbers, rest)
    }

    override def compareTo(other: Version): Int = {
      var diff = 0
      diff = numbers(0) - other.numbers(0)
      if (diff == 0) {
        diff = numbers(1) - other.numbers(1)
        if (diff == 0) {
          diff = numbers(2) - other.numbers(2)
          if (diff == 0) {
            diff = rest.compareTo(other.rest)
          }
        }
      }
      diff
    }

    override def equals(o: Any): Boolean = o match {
      case v: Version ⇒ compareTo(v) == 0
      case _          ⇒ false
    }

    override def toString: String = version

    override def hashCode(): Int = {
      val state = Seq(numbers, rest, version)
      state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
    }
  }
}

trait ProjectFamily {
  def familyName: String
  def libraries: immutable.Seq[String]
}

object AkkaHttpProjectFamily extends ProjectFamily {

  val familyName = "Akka Http"

  val libraries = List(
    "akka-http",
    "akka-http-bench-jmh",
    "akka-http-caching",
    "akka-http-core",
    "akka-http-marshallers-java",
    "akka-http-marshallers-scala",
    "akka-http-testkit",
    "akka-http-tests",
    "akka-http2-support",
    "akka-parsing"
  )
}

object AkkaProjectFamily extends ProjectFamily {

  val familyName = "Akka"

  val libraries = List(
    "akka-actor",
    "akka-actor-testkit-typed",
    "akka-actor-typed",
    "akka-agent",
    "akka-camel",
    "akka-cluster",
    "akka-cluster-metrics",
    "akka-cluster-sharding",
    "akka-cluster-sharding-typed",
    "akka-cluster-tools",
    "akka-cluster-typed",
    "akka-distributed-data",
    "akka-multi-node-testkit",
    "akka-osgi",
    "akka-persistence",
    "akka-persistence-query",
    "akka-persistence-shared",
    "akka-persistence-typed",
    "akka-protobuf",
    "akka-remote",
    "akka-slf4j",
    "akka-stream",
    "akka-stream-testkit",
    "akka-stream-typed"
  )
}
