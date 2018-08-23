/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

import java.util.concurrent.CompletionStage

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.pattern.AskTimeoutException
import com.typesafe.config.ConfigFactory
import org.scalatest.{ AsyncFlatSpec, BeforeAndAfterAll, FlatSpec, Matchers }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest._

import scala.compat.java8.FutureConverters
import scala.concurrent.Future

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

  behavior of "TracingPersistentEntityResultHandler"

  it should "correctly add the payload of the message on the ask timeout" in {
    val config = new PersistentEntityTracingConfig(true, true)
    val resultHandler = new TracingPersistentEntityResultHandler(cluster, config, uselessId)
    val askTimeout = new AskTimeoutException("Timeout of entity")
    val cmd = Command("somehow")
    val future = resultHandler.mapResult(Future.failed(askTimeout), cmd)
    val futureEx = recoverToExceptionIf[Throwable](
      future
    )
    futureEx.map(ex => assert(ex.getMessage.contains(cmd.toString) && (ex.isInstanceOf[AskTimeoutException])))

  }

  it should "correctly ignore the payload of the message on the ask timeout" in {
    val config = new PersistentEntityTracingConfig(true, false)
    val resultHandler = new TracingPersistentEntityResultHandler(cluster, config, uselessId)
    val askTimeout = new AskTimeoutException("Timeout of entity")
    val cmd = Command("somehow")
    val future = resultHandler.mapResult(Future.failed(askTimeout), cmd)
    val futureEx = recoverToExceptionIf[Throwable](
      future
    )
    futureEx.map(ex => assert((!ex.getMessage.contains(cmd.toString)) && (ex.isInstanceOf[AskTimeoutException])))
  }

  it should "correctly ihandle successfulResult" in {
    val config = new PersistentEntityTracingConfig(true, false)
    val resultHandler = new TracingPersistentEntityResultHandler(cluster, config, uselessId)
    val successfulResult = Future("Hello world")
    val cmd = Command("somehow")
    val future = resultHandler.mapResult(successfulResult, cmd)
    future.map(ex => assert(ex === "Hello world"))
  }

}

case class Command(s: String) extends PersistentEntity.ReplyType[String]
