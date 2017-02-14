/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.dev

import java.io.Closeable
import java.net.URL
import java.util.concurrent.{ Executors, TimeUnit }

import com.lightbend.lagom.dev.Reloader.DevServer
import play.dev.filewatch.LoggerProxy

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._

/**
 * Helper for working with the console
 */
class ConsoleHelper(colors: Colors) {

  def printStartScreen(log: LoggerProxy, services: Seq[(String, String)]): Unit = {
    services.foreach {
      case (name, url) =>
        log.info(s"Service $name listening for HTTP on $url")
    }
    log.info(colors.green(s"(Service${if (services.size > 1) "s" else ""} started, press enter to stop and go back to the console...)"))
  }

  def blockUntilExit() = {
    // blocks until user presses enter
    System.in.read()
  }

  def shutdownAsynchronously(log: LoggerProxy, services: Seq[Closeable]) = {
    // shut down all running services
    log.info("Stopping services")

    val n = java.lang.Runtime.getRuntime.availableProcessors
    log.debug("nb proc : " + n)
    //creating a dedicated execution context
    // with a fixed number of thread (indexed on number of cpu)
    implicit val ecn = ExecutionContext.fromExecutorService(
      Executors.newFixedThreadPool(n)
    )

    try {
      //Stop services in asynchronous manner
      val closing = Future.traverse(services)(serv => Future {
        serv.close()
      })
      closing.onComplete(_ => log.info("All services are stopped"))
      Await.result(closing, 60.seconds)

      println()
      // and finally shut down any other possibly running embedded server
      Await.result(Servers.asyncTryStop(log), 60.seconds)
    } finally {
      // and the last part concern the closing of execution context that has been created above
      ecn.shutdown()
      ecn.awaitTermination(60, TimeUnit.SECONDS)
    }
  }
}

class Colors(logNoFormat: String) {
  import scala.Console._

  val isANSISupported = {
    Option(System.getProperty(logNoFormat)).map(_ != "true").orElse {
      Option(System.getProperty("os.name"))
        .map(_.toLowerCase(java.util.Locale.ENGLISH))
        .filter(_.contains("windows"))
        .map(_ => false)
    }.getOrElse(true)
  }

  private def color(code: String, str: String) = if (isANSISupported) code + str + RESET else str

  def red(str: String): String = color(RED, str)
  def blue(str: String): String = color(BLUE, str)
  def cyan(str: String): String = color(CYAN, str)
  def green(str: String): String = color(GREEN, str)
  def magenta(str: String): String = color(MAGENTA, str)
  def white(str: String): String = color(WHITE, str)
  def black(str: String): String = color(BLACK, str)
  def yellow(str: String): String = color(YELLOW, str)
}
