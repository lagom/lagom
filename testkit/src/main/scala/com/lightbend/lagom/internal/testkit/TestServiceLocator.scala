/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.testkit

import java.net.URI
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

import scala.compat.java8.FutureConverters._
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.lightbend.lagom.javadsl.api.{ Descriptor, ServiceLocator }
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraConfig
import javax.inject.Inject
import javax.inject.Singleton

import com.lightbend.lagom.internal.client.CircuitBreakers
import com.lightbend.lagom.javadsl.client.CircuitBreakingServiceLocator

@Singleton
private[lagom] class TestServiceLocator @Inject() (
  circuitBreakers: CircuitBreakers,
  port:            TestServiceLocatorPort,
  config:          CassandraConfig,
  implicit val ec: ExecutionContext
) extends CircuitBreakingServiceLocator(circuitBreakers) {

  private val futureUri = port.port.map(p => URI.create("http://localhost:" + p))

  private val cassandraUris: Map[String, URI] =
    config.uris.asScala.map(contactPoint => contactPoint.name -> contactPoint.uri)(collection.breakOut)

  override def locate(name: String, call: Descriptor.Call[_, _]): CompletionStage[Optional[URI]] =
    cassandraUris.get(name) match {
      case None      => futureUri.map(uri => Optional.of(uri)).toJava
      case Some(uri) => CompletableFuture.completedFuture(Optional.of(uri))
    }

}

private[lagom] final case class TestServiceLocatorPort(port: Future[Int])

