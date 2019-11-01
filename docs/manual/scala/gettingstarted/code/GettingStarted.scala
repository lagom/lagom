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
  import akka.actor.typed.ActorRef
  import akka.actor.typed.Behavior
  import com.lightbend.lagom.scaladsl.api.ServiceCall
  import akka.cluster.sharding.typed.scaladsl.ClusterSharding
  import akka.cluster.sharding.typed.scaladsl.EntityRef

  import scala.concurrent.ExecutionContext
  import scala.concurrent.duration._
  import akka.util.Timeout
  import com.lightbend.lagom.scaladsl.api.transport.BadRequest

  class HelloServiceImpl(clusterSharding: ClusterSharding)(implicit ec: ExecutionContext) extends HelloService {
    implicit val timeout = Timeout(5.seconds)

    override def hello(id: String): ServiceCall[NotUsed, String] = ServiceCall { _ =>
      entityRef(id)
        .ask[Greeting](replyTo => Hello(id, replyTo))
        .map(greeting => greeting.message)
    }

    override def useGreeting(id: String) = ServiceCall { request =>
      entityRef(id)
        .ask[Confirmation](
          replyTo => UseGreetingMessage(request.message, replyTo)
        )
        .map {
          case Accepted => Done
          case _        => throw BadRequest("Can't upgrade the greeting message.")
        }
    }

    private def entityRef(id: String): EntityRef[HelloWorldCommand] =
      clusterSharding.entityRefFor(HelloWorldState.typeKey, id)
  }
  //#helloserviceimpl

  import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
  import com.lightbend.lagom.scaladsl.persistence.PersistentEntity

  sealed trait HelloWorldCommand
  case class UseGreetingMessage(message: String, replyTo: ActorRef[Confirmation]) extends HelloWorldCommand
  case class Hello(name: String, replyTo: ActorRef[Greeting])                     extends HelloWorldCommand

  final case class Greeting(message: String)
  sealed trait Confirmation
  sealed trait Accepted               extends Confirmation
  case object Accepted                extends Accepted
  case class Rejected(reason: String) extends Confirmation

  sealed trait HelloWorldEvent
  case class GreetingMessageChanged(message: String) extends HelloWorldEvent

  object HelloWorldState {
    import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
    val typeKey = EntityTypeKey[HelloWorldCommand]("HelloWorldAggregate")
  }
}
