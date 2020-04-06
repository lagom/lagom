/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.testkit

import java.nio.file.Files
import java.util.concurrent.TimeUnit

import akka.persistence.cassandra.testkit.CassandraLauncher
import com.google.common.io.MoreFiles
import com.google.common.io.RecursiveDeleteOption
import play.api.Logger
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future
import scala.util.Try

private[lagom] object CassandraTestServer {
  private val LagomTestConfigResource: String = "lagom-test-embedded-cassandra.yaml"

  private lazy val log = Logger(getClass)

  def run(cassandraDirectoryPrefix: String, lifecycle: ApplicationLifecycle): Int = {

    val cassandraDirectory = Files.createTempDirectory(cassandraDirectoryPrefix)

    // Shut down Cassandra and delete its temporary directory when the application shuts down
    lifecycle.addStopHook { () =>
      import scala.concurrent.ExecutionContext.Implicits.global
      Try(CassandraLauncher.stop())
      // The ALLOW_INSECURE option is required to remove the files on OSes that don't support SecureDirectoryStream
      // See http://google.github.io/guava/releases/snapshot-jre/api/docs/com/google/common/io/MoreFiles.html#deleteRecursively-java.nio.file.Path-com.google.common.io.RecursiveDeleteOption...-
      Future(MoreFiles.deleteRecursively(cassandraDirectory, RecursiveDeleteOption.ALLOW_INSECURE))
    }

    val t0 = System.nanoTime()

    CassandraLauncher.start(
      cassandraDirectory.toFile,
      LagomTestConfigResource,
      clean = false,
      port = 0,
      CassandraLauncher.classpathForResources(LagomTestConfigResource)
    )

    log.debug(s"Cassandra started in ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)} ms")

    CassandraLauncher.randomPort
  }
}
