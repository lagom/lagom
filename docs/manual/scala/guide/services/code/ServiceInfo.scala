/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.services

package helloserviceinfo {

  //#service-name
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

  //#service-name
}

package serviceacls {

  import akka.NotUsed
  import com.lightbend.lagom.scaladsl.api._

  trait CallRest extends Service {
    //#service-acls
    def login: ServiceCall[String, String]

    def descriptor = {
      import Service._
      import com.lightbend.lagom.scaladsl.api.transport.Method
      named("user-authentication")
        .withCalls(
          restCall(Method.POST, "/api/users/login", login)
        )
        .withAutoAcl(true)
    }

    //#service-acls
  }

}
