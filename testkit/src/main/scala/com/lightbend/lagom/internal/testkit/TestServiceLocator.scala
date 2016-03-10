/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.testkit

import javax.inject.Singleton
import java.net.URI
import java.util.Optional
import java.util.concurrent.CompletionStage
import com.lightbend.lagom.javadsl.api.ServiceLocator
import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import akka.persistence.cassandra.testkit.CassandraLauncher
import java.util.concurrent.CompletableFuture
import akka.actor.ActorSystem
import javax.inject.Inject
import com.lightbend.lagom.javadsl.persistence.InitServiceLocatorHolder

@Singleton
private[lagom] class TestServiceLocator @Inject() (system: ActorSystem, port: TestServiceLocatorPort) extends ServiceLocator {

  private val futureUri = port.port.map(p => URI.create("http://localhost:" + p))

  private val cassandraUris = InitServiceLocatorHolder.cassandraUrisFromConfig(system).map {
    case (name, uri) => name -> new URI(uri)
  }.toMap

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

