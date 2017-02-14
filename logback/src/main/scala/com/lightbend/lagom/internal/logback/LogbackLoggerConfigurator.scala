/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.logback

import java.io.File
import java.net.URL

import org.slf4j.LoggerFactory
import play.api._

import scala.util.control.NonFatal

object LogbackLoggerConfigurator {
  final private val DevLogbackConfig: String = "logback-lagom-dev.xml"
  final private val DefaultLogbackConfig: String = "logback-lagom-default.xml"
}

class LogbackLoggerConfigurator extends LoggerConfigurator {
  import LogbackLoggerConfigurator._

  /**
   * Initialize the Logger when there's no application ClassLoader available.
   */
  def init(rootPath: File, mode: Mode.Mode): Unit = {
    val properties = Map("application.home" -> rootPath.getAbsolutePath)
    val resourceName = if (mode == Mode.Dev) DevLogbackConfig else DefaultLogbackConfig
    val resourceUrl = Option(this.getClass.getClassLoader.getResource(resourceName))
    configure(properties, resourceUrl)
  }

  /**
   * Reconfigures the underlying logback infrastructure.
   */
  def configure(env: Environment): Unit = {
    val properties = Map("application.home" -> env.rootPath.getAbsolutePath)

    // Get an explicitly configured resource URL
    // Fallback to a file in the conf directory if the resource wasn't found on the classpath
    def explicitResourceUrl = sys.props.get("logger.resource").flatMap { r =>
      env.resource(r).map(_.toURI.toURL)
    }

    // Get an explicitly configured file URL
    def explicitFileUrl = sys.props.get("logger.file").map(new File(_).toURI.toURL)

    // logback.xml is the documented method, logback-lagom-default.xml is the fallback that Lagom uses
    // if no other file is found
    def resourceUrl = env.resource("logback.xml")
      .orElse(env.resource(
        if (env.mode == Mode.Dev) DevLogbackConfig else DefaultLogbackConfig
      ))

    val configUrl = explicitResourceUrl orElse explicitFileUrl orElse resourceUrl

    configure(properties, configUrl)
  }

  /**
   * Reconfigures the underlying logback infrastructure.
   */
  def configure(properties: Map[String, String], config: Option[URL]): Unit = synchronized {
    // Redirect JUL -> SL4FJ
    {
      import java.util.logging._

      import org.slf4j.bridge._

      Option(java.util.logging.Logger.getLogger("")).map { root =>
        root.setLevel(Level.FINEST)
        root.getHandlers.foreach(root.removeHandler(_))
      }

      SLF4JBridgeHandler.install()
    }

    // Configure logback
    {
      import ch.qos.logback.classic._
      import ch.qos.logback.classic.joran._
      import ch.qos.logback.core.util._
      import org.slf4j._

      try {
        val ctx = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
        val configurator = new JoranConfigurator
        configurator.setContext(ctx)
        ctx.reset()

        // Ensure that play.Logger and play.api.Logger are ignored when detecting file name and line number for
        // logging
        val frameworkPackages = ctx.getFrameworkPackages
        frameworkPackages.add(classOf[play.Logger].getName)
        frameworkPackages.add(classOf[play.api.Logger].getName)

        properties.foreach {
          case (name, value) => ctx.putProperty(name, value)
        }

        try {
          config match {
            case Some(url) => configurator.doConfigure(url)
            case None =>
              System.err.println("Could not detect a logback configuration file, not configuring logback")
          }
        } catch {
          case NonFatal(e) =>
            System.err.println("Error encountered while configuring logback:")
            e.printStackTrace()
        }

        StatusPrinter.printIfErrorsOccured(ctx)
      } catch {
        case NonFatal(_) =>
      }

    }

  }

  /**
   * Shutdown the logger infrastructure.
   */
  def shutdown(): Unit = synchronized {
    import ch.qos.logback.classic._

    val ctx = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    ctx.stop()

    org.slf4j.bridge.SLF4JBridgeHandler.uninstall()
  }
}
