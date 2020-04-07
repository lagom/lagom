/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.helloworld.api

import akka.Done
import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.Descriptor
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.api.Service._
import com.lightbend.lagom.scaladsl.api.ServiceCall

trait HelloWorldService extends Service {

  /**
    * Example: curl http://localhost:9000/api-scala//hello/Alice
    */
  def hello(id: String): ServiceCall[NotUsed, String]

  /**
    * We're using GET ops to change the state since the code in the scripted test is a lot simpler.
    * Example: curl http://localhost:9000/api-scala/set/Alice/Hi
    */
  def useGreeting(id: String, message:String): ServiceCall[NotUsed, Done]

  override final def descriptor: Descriptor = {
    // @formatter:off
    named("hello-scala")
      .withCalls(
        pathCall("/api-scala/hello/:id", hello _),
        pathCall("/api-scala/set/:id/:message", useGreeting _)
      )
    // @formatter:on
  }
}
