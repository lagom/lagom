/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.client.integration

import akka.actor.{ Actor, ActorRef, ActorSystem, Props }
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterEach, FlatSpec, Matchers, WordSpec }

import scala.concurrent.duration._
import scala.concurrent.Await
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.util.Timeout

class LagomClientFactorySpec extends FlatSpec with Matchers with BeforeAndAfterEach with ScalaFutures {

  private var system: ActorSystem = _
  private var echoActor: ActorRef = _
  implicit val timeout = Timeout(5.seconds)

  /**
   * This test checks that we can start an LagomClientFactory where an
   * ActorSystem is already running without failing because of binding the akka-remote port.
   */
  "LagomClientFactory" should "not bind on akka-remote port when using its own actor system" in {

    // check that actor system is operational
    (echoActor ? "hey").mapTo[String].futureValue shouldBe "hey"

    // create an close a factory should work
    // without conflicting with external ActorSystem (remote port binding)
    LagomClientFactory
      .create("test", this.getClass.getClassLoader)
      .close()

    // check that actor system is still operational
    (echoActor ? "hey").mapTo[String].futureValue shouldBe "hey"

  }

  /**
   * This test checks that a LagomClientFactory created while passing an external ActorSystem
   * won't shutdown the ActorSystem when closed.
   */
  "LagomClientFactory" should "when using a unmanaged actor system, shoudl not terminate it upon closing" in {

    // check that actor system is operational
    (echoActor ? "hey").mapTo[String].futureValue shouldBe "hey"

    LagomClientFactory
      // create a factory by passing an existing ActorSystem
      .create(
        "test",
        this.getClass.getClassLoader,
        system,
        ActorMaterializer()(system)
      )
      // closing the factory should not close the existing ActorSystem
      .close()

    // check that actor system is still operational
    (echoActor ? "hey").mapTo[String].futureValue shouldBe "hey"
  }

  override protected def beforeEach(): Unit = {
    // this test will load src/test/resource/application.conf
    // that is configured to enable akka-remote
    system = ActorSystem("test", ConfigFactory.load())
    echoActor = system.actorOf(Props(new EchoActor), "echo")
  }

  class EchoActor extends Actor {
    override def receive: Receive = {
      case s: String => sender() ! s
    }
  }
  override protected def afterEach(): Unit = {
    Await.ready(system.terminate(), 5.seconds)
  }
}
