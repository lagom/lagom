/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.cluster.pubsub

package example {

  import akka.NotUsed
  import akka.stream.scaladsl.Source
  import com.lightbend.lagom.scaladsl.api.Service
  import com.lightbend.lagom.scaladsl.api.ServiceCall
  import play.api.libs.json.Format
  import play.api.libs.json.Json

  import scala.concurrent.Future

  case class Temperature(value: Double)

  object Temperature {
    implicit val format: Format[Temperature] = Json.format
  }

  //#service-api
  trait SensorService extends Service {
    def registerTemperature(id: String): ServiceCall[Temperature, NotUsed]

    def temperatureStream(id: String): ServiceCall[NotUsed, Source[Temperature, NotUsed]]

    def descriptor = {
      import Service._

      named("/sensorservice").withCalls(
        pathCall("/device/:id/temperature", registerTemperature _),
        pathCall("/device/:id/temperature/stream", temperatureStream _)
      )
    }
  }
  //#service-api

  //#service-impl
  import com.lightbend.lagom.scaladsl.pubsub.PubSubRegistry
  import com.lightbend.lagom.scaladsl.pubsub.TopicId

  class SensorServiceImpl(pubSub: PubSubRegistry) extends SensorService {
    def registerTemperature(id: String) = ServiceCall { temperature =>
      val topic = pubSub.refFor(TopicId[Temperature](id))
      topic.publish(temperature)
      Future.successful(NotUsed.getInstance())
    }

    def temperatureStream(id: String) = ServiceCall { _ =>
      val topic = pubSub.refFor(TopicId[Temperature](id))
      Future.successful(topic.subscriber)
    }
  }
  //#service-impl

}

package serviceimplstream {
  import example.Temperature
  import akka.NotUsed
  import akka.stream.scaladsl.Source
  import com.lightbend.lagom.scaladsl.api.Service._
  import com.lightbend.lagom.scaladsl.api.Service
  import com.lightbend.lagom.scaladsl.api.ServiceCall
  import com.lightbend.lagom.scaladsl.pubsub.PubSubRegistry
  import com.lightbend.lagom.scaladsl.pubsub.TopicId

  import scala.concurrent.Future

  trait SensorService extends Service {
    def registerTemperature(id: String): ServiceCall[Source[Temperature, NotUsed], NotUsed]
    def temperatureStream(id: String): ServiceCall[NotUsed, Source[Temperature, NotUsed]]
    def descriptor = {
      import Service._
      named("/sensorservice").withCalls(
        pathCall("/device/:id/temperature", registerTemperature _),
        pathCall("/device/:id/temperature/stream", temperatureStream _)
      )
    }
  }

  //#service-impl-stream
  import akka.stream.Materializer

  class SensorServiceImpl(pubSub: PubSubRegistry)(implicit materializer: Materializer) extends SensorService {

    def registerTemperature(id: String) = ServiceCall { temperatures =>
      val topic = pubSub.refFor(TopicId[Temperature](id))
      temperatures.runWith(topic.publisher)
      Future.successful(NotUsed.getInstance())
    }

    def temperatureStream(id: String) = ServiceCall { _ =>
      val topic = pubSub.refFor(TopicId[Temperature](id))
      Future.successful(topic.subscriber)
    }
  }
  //#service-impl-stream

}

package persistententity {

  import akka.Done
  import akka.NotUsed
  import akka.stream.scaladsl.Source
  import com.lightbend.lagom.scaladsl.api.transport.Method
  import com.lightbend.lagom.scaladsl.api.Service
  import com.lightbend.lagom.scaladsl.api.ServiceCall
  import docs.home.scaladsl.persistence._
  import play.api.libs.json.Format
  import play.api.libs.json.Json

  import scala.concurrent.Future

  //#persistent-entity-inject
  import com.lightbend.lagom.scaladsl.persistence.PersistentEntity
  import com.lightbend.lagom.scaladsl.pubsub.PubSubRegistry
  import com.lightbend.lagom.scaladsl.pubsub.TopicId

  final class Post(pubSubRegistry: PubSubRegistry) extends PersistentEntity {
    private val publishedTopic = pubSubRegistry.refFor(TopicId[PostPublished])
    //#persistent-entity-inject

    override type Command = BlogCommand
    override type Event   = BlogEvent
    override type State   = BlogState

    override def initialState: BlogState = BlogState.empty

    override def behavior: Behavior = {
      case state if state.isEmpty  => initial
      case state if !state.isEmpty => postAdded
    }

    private val initial: Actions = {
      Actions()
        .onCommand[AddPost, AddPostDone] {
          case (AddPost(content), ctx, state) =>
            ctx.thenPersist(PostAdded(entityId, content)) { evt =>
              ctx.reply(AddPostDone(entityId))
            }
        }
        .onCommand[AddPost, AddPostDone] {
          case (AddPost(content), ctx, state) =>
            if (content.title == null || content.title.equals("")) {
              ctx.invalidCommand("Title must be defined")
              ctx.done
            } else {
              ctx.thenPersist(PostAdded(entityId, content)) { evt =>
                ctx.reply(AddPostDone(entityId))
              }
            }
        }
        .onEvent {
          case (PostAdded(postId, content), state) =>
            BlogState(Some(content), published = false)
        }
    }

    private val postAdded: Actions = {
      Actions()
        .onCommand[ChangeBody, Done] {
          case (ChangeBody(body), ctx, state) =>
            ctx.thenPersist(BodyChanged(entityId, body))(_ => ctx.reply(Done))
        }
        //#persistent-entity-publish
        .onCommand[Publish.type, Done] {
          case (Publish, ctx, state) =>
            ctx.thenPersist(PostPublished(entityId)) { evt =>
              ctx.reply(Done)
              publishedTopic.publish(evt)
            }
        }
        //#persistent-entity-publish
        .onEvent {
          case (BodyChanged(_, body), state) =>
            state.withBody(body)
        }
        .onReadOnlyCommand[GetPost.type, PostContent] {
          case (GetPost, ctx, state) =>
            ctx.reply(state.content.get)
        }
    }

  }

  trait BlogService extends Service {
    def publishedStream: ServiceCall[NotUsed, Source[PostPublished, NotUsed]]

    override def descriptor = {
      import Service._
      implicit val postPublishedFormat: Format[PostPublished] = Json.format
      named("blogservice").withCalls(
        restCall(Method.GET, "/blogs/published", publishedStream)
      )
    }
  }

  //#entity-service-impl
  class BlogServiceImpl(pubSubRegistry: PubSubRegistry) extends BlogService {
    private val publishedTopic = pubSubRegistry.refFor(TopicId[PostPublished])

    override def publishedStream = ServiceCall { _ =>
      Future.successful(publishedTopic.subscriber)
    }
  }
  //#entity-service-impl

}
