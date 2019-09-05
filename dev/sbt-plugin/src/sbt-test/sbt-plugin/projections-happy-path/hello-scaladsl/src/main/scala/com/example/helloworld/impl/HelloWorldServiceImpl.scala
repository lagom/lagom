/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.helloworld.impl

import com.example.helloworld.api.HelloWorldService
import com.example.helloworld.impl.readsides.StartedReadSideProcessor
import com.example.helloworld.impl.readsides.StoppedReadSideProcessor
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry

import scala.concurrent.ExecutionContext

class HelloWorldServiceImpl(persistentEntityRegistry: PersistentEntityRegistry,
                            startedProcessor: StartedReadSideProcessor,
                            stoppedProcessor: StoppedReadSideProcessor)
                           (implicit exCtx:ExecutionContext)
    extends HelloWorldService {


  override def hello(id: String) = ServiceCall { _ =>
    ref(id)
      .ask(Hello(id))
      .map { msg =>
        s"""$msg
         |Started reports: ${startedProcessor.getLastMessage(id)}
         |Stopped reports: ${stoppedProcessor.getLastMessage(id)}
         |""".stripMargin
      }
  }

  override def useGreeting(id: String, message: String) = ServiceCall { _ =>
    ref(id).ask(UseGreetingMessage(message))
  }

  private def ref(id: String) =  persistentEntityRegistry.refFor[HelloWorldEntity](id)

}
