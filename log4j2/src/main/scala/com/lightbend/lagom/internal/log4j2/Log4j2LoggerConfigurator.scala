/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.log4j2

import java.io.File
import java.net.URL

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configurator
import org.slf4j.ILoggerFactory
import org.slf4j.impl.StaticLoggerBinder
import play.api._

object Log4j2LoggerConfigurator {
  final private val DevLog4j2Config: String = "log4j2-lagom-dev.xml"
  final private val DefaultLog4j2Config: String = "log4j2-lagom-default.xml"
}

class Log4j2LoggerConfigurator extends LoggerConfigurator {

  import Log4j2LoggerConfigurator._

  override def loggerFactory: ILoggerFactory = {
    StaticLoggerBinder.getSingleton.getLoggerFactory
  }

  override def init(rootPath: File, mode: Mode): Unit = {
    val properties = Map("application.home" -> rootPath.getAbsolutePath)
    val resourceName = if (mode == Mode.Dev) DevLog4j2Config else DefaultLog4j2Config
    val resourceUrl = Option(this.getClass.getClassLoader.getResource(resourceName))
    configure(properties, resourceUrl)
  }

  override def configure(env: Environment): Unit =
    configure(env, Configuration.empty, Map.empty)

  override def configure(env: Environment, configuration: Configuration, optionalProperties: Map[String, String]): Unit = {
    val properties = LoggerConfigurator.generateProperties(env, configuration, optionalProperties)
    configure(properties, configUrl(env))
  }

  override def configure(properties: Map[String, String], config: Option[URL]): Unit = {
    if (config.isEmpty) {
      System.err.println("Could not detect a log4j2 configuration file, not configuring log4j2")
    }

    config.foreach { url =>
      val context = LogManager.getContext(false).asInstanceOf[LoggerContext]
      context.setConfigLocation(url.toURI)
    }
  }

  private def configUrl(env: Environment) = {
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

    explicitResourceUrl orElse explicitFileUrl orElse resourceUrl
  }

  override def shutdown(): Unit = LogManager.shutdown()
}
