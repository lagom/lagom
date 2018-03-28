/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence

import java.util.concurrent.CompletionStage

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.pattern.AskTimeoutException
import com.typesafe.config.ConfigFactory
import org.scalatest.{ AsyncFlatSpec, BeforeAndAfterAll, FlatSpec, Matchers }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest._

import scala.compat.java8.FutureConverters

class TracingPersistentEntityErrorHandlerSpec extends AsyncFlatSpec
  with Matchers
  with BeforeAndAfterAll
  with ScalaFutures with RecoverMethods {

  @volatile var system: ActorSystem = _
  @volatile var cluster: Cluster = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    val config = ConfigFactory.parseString(
      "akka.actor.provider = akka.cluster.ClusterActorRefProvider \n" +
        "akka.remote.netty.tcp.port = 0 \n" +
        "akka.remote.netty.tcp.hostname = 127.0.0.1 \n" +
        "akka.loglevel = INFO \n" +
        "lagom.persistence.error-tracing.log-cluster-state-on-timeout = true \n" +
        "lagom.persistence.error-tracing.log-command-payload-on-failure = true \n"
    )
    system = ActorSystem.create("PubSubTest", config)
    cluster = Cluster(system)
    cluster.join(Cluster(system).selfAddress)
  }

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }

  val uselessId = "123"

  // ------------------------------------------------------------

  behavior of "TracingPersistentEntityErrorHandler"

  it should "correctly add the payload of the message on the ask timeout" in {
    val config = new ErrorTracingConfig(true, true)
    val errorHandler = new TracingPersistentEntityErrorHandler(cluster, config, uselessId)
    val askTimeout = new AskTimeoutException("Timeout of entity")
    val cmd = Command("somehow")
    val completionStage: CompletionStage[String] = errorHandler.handleAskFailure(askTimeout, cmd)
    val future = FutureConverters.toScala(completionStage)
    val futureEx = recoverToExceptionIf[Throwable](
      future
    )
    futureEx.map(ex => assert(ex.getMessage.contains(cmd.toString)))

  }

  it should "correctly ignore the payload of the message on the ask timeout" in {
    val config = new ErrorTracingConfig(true, false)
    val errorHandler = new TracingPersistentEntityErrorHandler(cluster, config, uselessId)
    val askTimeout = new AskTimeoutException("Timeout of entity")
    val cmd = Command("somehow")
    val completionStage: CompletionStage[String] = errorHandler.handleAskFailure(askTimeout, cmd)
    val future = FutureConverters.toScala(completionStage)
    val futureEx = recoverToExceptionIf[Throwable](
      future
    )
    futureEx.map(ex => assert(!ex.getMessage.contains(cmd.toString)))

  }

}

case class Command(s: String) extends PersistentEntity.ReplyType[String]
