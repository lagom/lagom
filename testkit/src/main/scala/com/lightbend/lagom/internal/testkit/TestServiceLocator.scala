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

import com.lightbend.lagom.javadsl.api.ServiceLocator
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraConfig
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraContactPoint

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
private[lagom] class TestServiceLocator @Inject() (
  port:            TestServiceLocatorPort,
  config:          CassandraConfig,
  implicit val ec: ExecutionContext
) extends ServiceLocator {

  private val futureUri = port.port.map(p => URI.create("http://localhost:" + p))

  private val cassandraUris: Map[String, URI] =
    (config.uris.asScala.map(contactPoint => contactPoint.name -> contactPoint.uri))(collection.breakOut)

  override def doWithService[R](name: String, block: java.util.function.Function[URI, CompletionStage[R]]): CompletionStage[Optional[R]] = {
    val result = cassandraUris.get(name) match {
      case None      => futureUri.flatMap { uri => block(uri).toScala }
      case Some(uri) => block(uri).toScala
    }

    result.map(t => Optional.ofNullable(t)).toJava
  }

  override def locate(name: String): CompletionStage[Optional[URI]] =
    cassandraUris.get(name) match {
      case None      => futureUri.map(uri => Optional.of(uri)).toJava
      case Some(uri) => CompletableFuture.completedFuture(Optional.of(uri))
    }

}

private[lagom] final case class TestServiceLocatorPort(port: Future[Int])

