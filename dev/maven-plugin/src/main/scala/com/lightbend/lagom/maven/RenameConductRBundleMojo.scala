/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.maven

import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import javax.inject.Inject

import better.files.{ File => ScalaFile, _ }

import scala.beans.BeanProperty
import scala.collection.JavaConverters._

/**
 * Converts a zip artifact to one named according to ConductR's conventions.
 */
class RenameConductRBundleMojo @Inject() (logger: MavenLoggerProxy) extends LagomAbstractMojo {

  @BeanProperty
  var sourceConductRBundle: File = _

  @BeanProperty
  var bundleName: String = _

  @BeanProperty
  var outputDirectory: File = _

  override def execute(): Unit = {
    if (!sourceConductRBundle.exists()) {
      logger.error(s"The source ConductR bundle, ${sourceConductRBundle.getAbsolutePath}, does not exist. Make sure you have " +
        s"configured your build to produce a ConductR zip artifact before running lagom:renameConductRBundle, and " +
        s"that either that artifact is located at ${sourceConductRBundle.getAbsolutePath}, or you have configured " +
        s"sourceConductRBundle to point to it. If you are using the maven assembly plugin to produce the artifact, " +
        s"ensure it is declared in your POM before the lagom plugin, as Maven executions for the same phase are " +
        s"executed in the order that they appear in the POM.")
      sys.error(s"The source ConductR bundle, ${sourceConductRBundle.getAbsolutePath}, does not exist.")
    }

    // We could do some validation here if we wanted - eg verify that bundle.conf exists, that it has the right service
    // name.

    val name = Option(bundleName).orElse {
      // Use the name of the first directory in the structure as the name of the bundle
      val zipFile = new ZipFile(sourceConductRBundle)
      try {
        zipFile.entries().asScala.take(1).foldLeft(Option.empty[String]) {
          case (_, entry) =>
            val rootDir = entry.getName.takeWhile(c => c != '/' && c != '\\')
            logger.debug(s"First entry in zip file was ${entry.getName}, using $rootDir as the bundle name")
            Some(rootDir)
        }
      } finally {
        zipFile.close()
      }
    }.getOrElse {
      sys.error(s"${sourceConductRBundle.getAbsolutePath} is an empty zip file.")
    }

    val bundleHash = sourceConductRBundle.toScala.checksum("SHA-256").toLowerCase(Locale.ENGLISH)
    val bundleFileName = s"$name-$bundleHash.zip"
    val outputDir = outputDirectory.toScala
    outputDir.createIfNotExists(asDirectory = true)
    val bundleFile = outputDir / bundleFileName

    if (!bundleFile.exists) {
      sourceConductRBundle.toScala.copyTo(bundleFile)
      logger.info(s"Created ConductR bundle $bundleFile")
    } else {
      logger.info(s"Not creating $bundleFile since it already exists")
    }
  }
}
