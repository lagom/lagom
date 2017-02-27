/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.testkit

import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.server.{ LagomApplicationContext, LocalServiceLocator }
import com.lightbend.lagom.scaladsl.testkit.services._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{ Seconds, Span }
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }

class ProducerStubSpec extends WordSpec with Matchers with BeforeAndAfterAll with Eventually {

  var producerStub: ProducerStub[AlphaEvent] = _

  private val stubbedApplication: LagomApplicationContext => DownstreamApplication = {
    ctx =>
      new DownstreamApplication(ctx) with LocalServiceLocator {
        val stubFactory = new ProducerStubFactory(actorSystem, materializer)
        producerStub = stubFactory.producer[AlphaEvent](AlphaService.TOPIC_ID)
        override lazy val alphaService = new AlphaServiceStub(producerStub)
      }
  }

  "The ProducerStub" should {
    "send message to consuming services" in ServiceTest.withServer(ServiceTest.defaultSetup)(stubbedApplication) { server =>
      implicit val exCtx = server.application.actorSystem.dispatcher
      producerStub.send(AlphaEvent(22))
      eventually(timeout(Span(5, Seconds))) {
        server.serviceClient.implement[CharlieService].messages.invoke().map { response =>
          response should ===(Seq(ReceivedMessage("A", 22)))
        }.recover {
          case t: Throwable => fail(t)
        }
      }
    }
  }

}

class AlphaServiceStub(stub: ProducerStub[AlphaEvent]) extends AlphaService {
  override def messages: Topic[AlphaEvent] = stub.topic

}
