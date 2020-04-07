/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.client.integration

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.BeforeAndAfterEach

import scala.concurrent.duration._
import scala.concurrent.Await
import akka.pattern._
import akka.stream.SystemMaterializer
import akka.util.Timeout
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LagomClientFactorySpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach with ScalaFutures {
  private var system: ActorSystem = _
  private var echoActor: ActorRef = _
  implicit val timeout            = Timeout(5.seconds)

  /**
   * This test checks that a LagomClientFactory created while passing an external ActorSystem
   * won't shutdown the ActorSystem when closed.
   */
  "LagomClientFactory" should "when using a unmanaged actor system, shoudl not terminate it upon closing" in {
    // check that actor system is operational
    (echoActor ? "hey").mapTo[String].futureValue shouldBe "hey"

    LagomClientFactory
    // create a factory by passing the existing ActorSystem
      .create(
        "test",
        this.getClass.getClassLoader,
        system,
        SystemMaterializer(system).materializer
      )
      // closing the factory should not close the existing ActorSystem
      .close()

    // check that actor system is still operational
    (echoActor ? "hey").mapTo[String].futureValue shouldBe "hey"
  }

  protected override def beforeEach(): Unit = {
    system = ActorSystem("test", ConfigFactory.load())
    echoActor = system.actorOf(Props(new EchoActor), "echo")
  }

  class EchoActor extends Actor {
    override def receive: Receive = {
      case s: String => sender() ! s
    }
  }
  protected override def afterEach(): Unit = {
    Await.ready(system.terminate(), 5.seconds)
  }
}
