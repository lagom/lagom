/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.gettingstarted

package helloservice {

  //#helloservice
  import akka.Done
  import akka.NotUsed
  import com.lightbend.lagom.scaladsl.api._
  import play.api.libs.json._

  trait HelloService extends Service {

    def hello(id: String): ServiceCall[NotUsed, String]

    def useGreeting(id: String): ServiceCall[GreetingMessage, Done]

    final override def descriptor = {
      import Service._
      named("hello")
        .withCalls(
          pathCall("/api/hello/:id", hello _),
          pathCall("/api/hello/:id", useGreeting _)
        )
        .withAutoAcl(true)
    }
  }

  case class GreetingMessage(message: String)

  object GreetingMessage {
    implicit val format: Format[GreetingMessage] = Json.format[GreetingMessage]
  }
  //#helloservice

  //#helloserviceimpl
  import com.lightbend.lagom.scaladsl.api.ServiceCall
  import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry

  class HelloServiceImpl(persistentEntityRegistry: PersistentEntityRegistry) extends HelloService {

    override def hello(id: String) = ServiceCall { _ =>
      val ref = persistentEntityRegistry.refFor[HelloEntity](id)
      ref.ask(Hello(id, None))
    }

    override def useGreeting(id: String) = ServiceCall { request =>
      val ref = persistentEntityRegistry.refFor[HelloEntity](id)
      ref.ask(UseGreetingMessage(request.message))
    }
  }
  //#helloserviceimpl

  import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
  import com.lightbend.lagom.scaladsl.persistence.PersistentEntity

  sealed trait HelloCommand
  case class Hello(id: String, timestamp: Option[String]) extends ReplyType[String] with HelloCommand
  case class UseGreetingMessage(msg: String)              extends ReplyType[Done] with HelloCommand
  sealed trait HelloEvent
  case class HelloState()
  class HelloEntity extends PersistentEntity {
    override type Command = HelloCommand
    override type Event   = HelloEvent
    override type State   = HelloState
    override def initialState = HelloState()
    override def behavior     = PartialFunction.empty
  }

}
