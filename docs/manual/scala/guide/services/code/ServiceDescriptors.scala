/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.services

package helloservice {

  //#hello-service
  import com.lightbend.lagom.scaladsl.api._

  trait HelloService extends Service {
    def sayHello: ServiceCall[String, String]

    override def descriptor = {
      import Service._
      named("hello").withCalls(
        call(sayHello)
      )
    }
  }
  //#hello-service
}

package servicecall {

  import scala.concurrent.Future

  //#service-call
  trait ServiceCall[Request, Response] {
    def invoke(request: Request): Future[Response]
  }
  //#service-call
}

package callidname {

  import com.lightbend.lagom.scaladsl.api._

  trait HelloService extends Service {
    def sayHello: ServiceCall[String, String]

    override def descriptor = {
      import Service._
      //#call-id-name
      named("hello").withCalls(
        namedCall("hello", sayHello)
      )
      //#call-id-name
    }
  }
}

package calllongid {
  import akka.NotUsed
  import com.lightbend.lagom.scaladsl.api._

  trait CallLongId extends Service {
    type Order = String
    //#call-long-id
    def getOrder(orderId: Long): ServiceCall[NotUsed, Order]

    override def descriptor = {
      import Service._
      named("orders").withCalls(
        pathCall("/order/:id", getOrder _)
      )
    }
    //#call-long-id
  }
}

package callcomplexid {
  import akka.NotUsed
  import com.lightbend.lagom.scaladsl.api._

  trait CallComplexId extends Service {
    type Item = String
    //#call-complex-id
    def getItem(orderId: Long, itemId: String): ServiceCall[NotUsed, Item]

    override def descriptor = {
      import Service._
      named("orders").withCalls(
        pathCall("/order/:orderId/item/:itemId", getItem _)
      )
    }
    //#call-complex-id
  }
}

package callquerystringparameters {
  import akka.NotUsed
  import com.lightbend.lagom.scaladsl.api._

  trait CallQueryStringParameters extends Service {
    type Item = String
    //#call-query-string-parameters
    def getItems(orderId: Long, pageNo: Int, pageSize: Int): ServiceCall[NotUsed, Seq[Item]]

    override def descriptor = {
      import Service._
      named("orders").withCalls(
        pathCall("/order/:orderId/items?pageNo&pageSize", getItems _)
      )
    }
    //#call-query-string-parameters
  }
}

package callrest {

  import akka.NotUsed
  import com.lightbend.lagom.scaladsl.api._

  trait CallRest extends Service {
    type Item = String
    //#call-rest
    def addItem(orderId: Long): ServiceCall[Item, NotUsed]
    def getItem(orderId: Long, itemId: String): ServiceCall[NotUsed, Item]
    def deleteItem(orderId: Long, itemId: String): ServiceCall[NotUsed, NotUsed]

    def descriptor = {
      import Service._
      import com.lightbend.lagom.scaladsl.api.transport.Method
      named("orders").withCalls(
        restCall(Method.POST, "/order/:orderId/item", addItem _),
        restCall(Method.GET, "/order/:orderId/item/:itemId", getItem _),
        restCall(Method.DELETE, "/order/:orderId/item/:itemId", deleteItem _)
      )
    }
    //#call-rest
  }
}

package callstream {
  import com.lightbend.lagom.scaladsl.api._

  trait CallStream extends Service {
    //#call-stream
    import akka.NotUsed
    import akka.stream.scaladsl.Source

    def tick(interval: Int): ServiceCall[String, Source[String, NotUsed]]

    def descriptor = {
      import Service._
      named("clock").withCalls(
        pathCall("/tick/:interval", tick _)
      )
    }
    //#call-stream
  }
}

package hellostream {
  import com.lightbend.lagom.scaladsl.api._

  trait HelloStream extends Service {
    //#hello-stream
    import akka.NotUsed
    import akka.stream.scaladsl.Source

    def sayHello: ServiceCall[Source[String, NotUsed], Source[String, NotUsed]]

    def descriptor = {
      import Service._
      named("hello").withCalls(
        call(this.sayHello)
      )
    }
    //#hello-stream
  }

}

package jsonmessages {

  //#user-class
  case class User(
      id: Long,
      name: String,
      email: Option[String]
  )
  //#user-class

  //#user-format
  object User {
    import play.api.libs.json._
    implicit val format: Format[User] = Json.format[User]
  }
  //#user-format

}
