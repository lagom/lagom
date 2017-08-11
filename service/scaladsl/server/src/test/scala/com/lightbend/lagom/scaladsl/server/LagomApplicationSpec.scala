/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.server

import akka.NotUsed
import com.lightbend.lagom.internal.scaladsl.api.broker.TopicFactoryProvider
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.api.{ AdditionalConfiguration, ProvidesAdditionalConfiguration, Service, ServiceCall }
import com.lightbend.lagom.scaladsl.api.broker.Topic.TopicId
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.typesafe.config.ConfigFactory
import org.scalatest.{ Matchers, WordSpec }
import play.api.ApplicationLoader.Context
import play.api.{ Configuration, Environment }
import play.api.inject.DefaultApplicationLifecycle
import play.api.libs.ws.ahc.AhcWSComponents
import play.core.DefaultWebCommands

import scala.concurrent.Future

class LagomApplicationSpec extends WordSpec with Matchers {

  "The Lagom Application" should {
    "fail to start if there are topics to publish but no topic publisher is provided" in {
      // Need to provide our own lifecycle so we can shutdown any components that started
      val lifecycle = new DefaultApplicationLifecycle
      a[LagomServerTopicFactoryVerifier.NoTopicPublisherException] should be thrownBy {
        new LagomApplication(LagomApplicationContext.Test) with AhcWSComponents {
          override lazy val applicationLifecycle = lifecycle
          override def lagomServer = serverFor[AppWithTopics](AppWithTopics)
          override def serviceLocator = NoServiceLocator
        }
      }
      lifecycle.stop()
    }

    "start if there are topics to publish but and no topic publisher is provided" in {
      new LagomApplication(LagomApplicationContext.Test) with AhcWSComponents {
        override def lagomServer = serverFor[AppWithNoTopics](AppWithNoTopics)
        override def serviceLocator = NoServiceLocator
      }.applicationLifecycle.stop()
    }

    "start if there are topics to publish and a topic publisher is provided" in {
      new LagomApplication(LagomApplicationContext.Test) with AhcWSComponents with MockTopicComponents {
        override def lagomServer = serverFor[AppWithTopics](AppWithTopics)
        override def serviceLocator = NoServiceLocator
      }.applicationLifecycle.stop()
    }

    "preserve config settings provided via ProvidesAdditionalConfiguration trait extension" in {
      val contextConfig = Configuration(ConfigFactory.parseString(configKey + "=\"via context\""))
      val expected = Configuration(ConfigFactory.parseString(configKey + "=\"via additional\""))

      val context = LagomApplicationContext(Context(Environment.simple(), None, new DefaultWebCommands, contextConfig,
        new DefaultApplicationLifecycle))
      new LagomApplication(context) with AhcWSComponents with FakeComponent {
        configuration.get[String](configKey) shouldBe expected.get[String](configKey)

        // following is required to complete the cake. Irrelevant for the test.
        override def lagomServer = serverFor[AppWithNoTopics](AppWithNoTopics)

        override def serviceLocator = NoServiceLocator
      }.applicationLifecycle.stop()
    }

  }
  private val configKey = "akka.cluster.seed-nodes"

  trait FakeComponent extends ProvidesAdditionalConfiguration {
    override def additionalConfiguration: AdditionalConfiguration = super.additionalConfiguration ++
      Configuration(ConfigFactory.parseString(configKey + "=\"via additional\""))
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
