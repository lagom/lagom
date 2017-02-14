/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server

import akka.NotUsed
import com.lightbend.lagom.internal.scaladsl.api.broker.TopicFactoryProvider
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.api.{ Service, ServiceCall }
import com.lightbend.lagom.scaladsl.api.broker.Topic.TopicId
import com.lightbend.lagom.scaladsl.api.broker.Topic
import org.scalatest.{ Matchers, WordSpec }
import play.api.inject.DefaultApplicationLifecycle
import play.api.libs.ws.ahc.AhcWSComponents

import scala.concurrent.Future

class LagomApplicationSpec extends WordSpec with Matchers {

  "The Lagom Application" should {
    "fail to start if there are topics to publish but no topic publisher is provided" in {
      // Need to provide our own lifecycle so we can shutdown any components that started
      val lifecycle = new DefaultApplicationLifecycle
      a[LagomServerTopicFactoryVerifier.NoTopicPublisherException] should be thrownBy {
        new LagomApplication(LagomApplicationContext.Test) with AhcWSComponents {
          override lazy val applicationLifecycle = lifecycle
          override def lagomServer = LagomServer.forServices(bindService[AppWithTopics].to(AppWithTopics))
          override def serviceLocator = NoServiceLocator
        }
      }
      lifecycle.stop()
    }

    "start if there are topics to publish but and no topic publisher is provided" in {
      new LagomApplication(LagomApplicationContext.Test) with AhcWSComponents {
        override def lagomServer = LagomServer.forServices(bindService[AppWithNoTopics].to(AppWithNoTopics))
        override def serviceLocator = NoServiceLocator
      }.applicationLifecycle.stop()
    }

    "start if there are topics to publish and a topic publisher is provided" in {
      new LagomApplication(LagomApplicationContext.Test) with AhcWSComponents with MockTopicComponents {
        override def lagomServer = LagomServer.forServices(bindService[AppWithTopics].to(AppWithTopics))
        override def serviceLocator = NoServiceLocator
      }.applicationLifecycle.stop()
    }
  }

  trait MockTopicComponents extends TopicFactoryProvider {
    override def topicPublisherName: Option[String] = Some("mock")
  }

  trait AppWithTopics extends Service {
    def someTopic: Topic[String]

    override def descriptor = {
      import Service._
      named("app-with-topics").withTopics(
        topic("some-topic", someTopic)
      )
    }
  }

  object AppWithTopics extends AppWithTopics {
    override def someTopic: Topic[String] = MockTopic
  }

  object MockTopic extends Topic[String] {
    override def topicId = TopicId("foo")
    override def subscribe = ???
  }

  trait AppWithNoTopics extends Service {
    def foo: ServiceCall[NotUsed, NotUsed]
    override def descriptor = Service.named("app-with-no-topics")
  }

  object AppWithNoTopics extends AppWithNoTopics {
    override def foo = ServiceCall { _ =>
      Future.successful(NotUsed)
    }
  }

}
