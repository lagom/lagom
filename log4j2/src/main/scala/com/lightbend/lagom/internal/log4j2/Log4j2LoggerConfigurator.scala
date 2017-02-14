/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.log4j2

import java.io.File
import java.net.URL

import play.api._

object Log4j2LoggerConfigurator {
  final private val DevLog4j2Config: String = "log4j2-lagom-dev.xml"
  final private val DefaultLog4j2Config: String = "log4j2-lagom-default.xml"
}

class Log4j2LoggerConfigurator extends LoggerConfigurator {

  import Log4j2LoggerConfigurator._

  override def init(rootPath: File, mode: Mode.Mode): Unit = {
    val properties = Map("application.home" -> rootPath.getAbsolutePath)
    val resourceName = if (mode == Mode.Dev) DevLog4j2Config else DefaultLog4j2Config
    val resourceUrl = Option(this.getClass.getClassLoader.getResource(resourceName))
    configure(properties, resourceUrl)
  }

  override def configure(env: Environment): Unit = {
    val properties = Map("application.home" -> env.rootPath.getAbsolutePath)

    // Get an explicitly configured resource URL
    // Fallback to a file in the conf directory if the resource wasn't found on the classpath
    def explicitResourceUrl = sys.props.get("logger.resource").flatMap { r =>
      env.resource(r).map(_.toURI.toURL)
    }

    // Get an explicitly configured file URL
    def explicitFileUrl = sys.props.get("logger.file").map(new File(_).toURI.toURL)

    // log4j2.xml is the documented method, log4j2-lagom-default.xml is the fallback that Lagom uses
    // if no other file is found
    def resourceUrl = env.resource("log4j2.xml")
      .orElse(env.resource(
        if (env.mode == Mode.Dev) DevLog4j2Config else DefaultLog4j2Config
      ))

    val configUrl = explicitResourceUrl orElse explicitFileUrl orElse resourceUrl

    configure(properties, configUrl)
  }

  override def configure(properties: Map[String, String], config: Option[URL]): Unit = {
    import org.apache.logging.log4j.core.config.Configurator
    import scala.util.control.NonFatal

    if (config.isEmpty) {
      System.err.println("Could not detect a log4j2 configuration file, not configuring log4j2")
      return
    }

    try {
      val ctx = Configurator.initialize("Lagom", this.getClass.getClassLoader, config.get.toURI)
    } catch {
      case NonFatal(e) =>
        System.err.println("Error encountered while configuring log4j2")
        e.printStackTrace()
    }
  }

  override def shutdown(): Unit = {
    import org.apache.logging.log4j.LogManager

    LogManager.shutdown()
  }
}
