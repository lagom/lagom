/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.dev

import java.io.File
import java.lang.ProcessBuilder.Redirect

import scala.collection.JavaConverters._

/**
 * Lagom process abstraction
 */
object LagomProcess {

  /**
   * Run a Java process.
   *
   * @param jvmArgs The JVM args.
   * @param classpath The classpath.
   * @param main The main class.
   * @param args The arguments.
   * @return The Java process.
   */
  def runJava(jvmArgs: List[String], classpath: Seq[File], main: String, args: List[String]): Process = {
    val javaBin = new File(new File(new File(sys.props("java.home")), "bin"), "java").getAbsolutePath

    val classpathString = classpath.map(_.getAbsolutePath).mkString(File.pathSeparator)

    val command = javaBin :: jvmArgs ::: "-classpath" :: classpathString :: main :: args

    new ProcessBuilder().redirectOutput(Redirect.INHERIT).redirectError(Redirect.INHERIT).command(command.asJava).start()
  }

}
