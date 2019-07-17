/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.cassandra

import java.io._
import java.util.Optional

import com.github.terma.javaniotcpproxy.{StaticTcpProxyConfig, TcpProxy}
import org.testcontainers.containers.CassandraContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.shaded.org.apache.commons.io.FileUtils

/**
 * Starts Cassandra.
 */
class CassandraLauncher {

  private var cassandraDaemon: Option[Closeable] = None

  private var server: CassandraContainer[_] = _
  private var proxy: TcpProxy = _

  var hostname: String = "127.0.0.1"
  var port: Int = 0

  def address = s"$hostname:$port"

  def start(
      cassandraDirectory: File,
      yamlConfig: File,
      clean: Boolean,
      port: Optional[Int], // Can't use scala.Option, because this method is called from another ClassLoader.
      loader: ClassLoader,
      maxWaiting: java.time.Duration
  ): Unit = this.synchronized {
    if (cassandraDaemon.isEmpty) {
      prepareCassandraDirectory(cassandraDirectory, clean)
      val currentClassLoader: ClassLoader = Thread.currentThread().getContextClassLoader
      try {
        Thread.currentThread().setContextClassLoader(loader)
        server = new CassandraContainer().withStartupAttempts(1)
        Thread.currentThread()
        server = server.withFileSystemBind(cassandraDirectory.getAbsolutePath, "/var/lib/cassandra")
        if (yamlConfig != null) {
          server = server.withFileSystemBind(yamlConfig.getAbsolutePath, "/etc/cassandra/cassandra.yaml")
        }
        server = server.waitingFor(new HostPortWaitStrategy().withStartupTimeout(maxWaiting))
        server.start()
        if (port.isPresent) {
          val config = new StaticTcpProxyConfig(port.get, server.getContainerIpAddress, server.getMappedPort(CassandraContainer.CQL_PORT))
          config.setWorkerCount(1)
          proxy = new TcpProxy(config)
          proxy.start()
        } else {
          hostname = server.getContainerIpAddress
        }
      } finally {
        Thread.currentThread().setContextClassLoader(currentClassLoader)
      }
      this.port = port.orElse(server.getMappedPort(CassandraContainer.CQL_PORT))
    }
  }

  private def prepareCassandraDirectory(cassandraDirectory: File, clean: Boolean): Unit = {
    if (clean)
      FileUtils.deleteDirectory(cassandraDirectory)

    if (!cassandraDirectory.exists)
      require(cassandraDirectory.mkdirs(), s"Couldn't create Cassandra directory [$cassandraDirectory]")
  }

  /**
   * Stops Cassandra.
   */
  def stop(): Unit = this.synchronized {
    cassandraDaemon.foreach(_.close())
    cassandraDaemon = None
  }

}
