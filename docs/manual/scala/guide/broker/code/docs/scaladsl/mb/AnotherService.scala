/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.mb

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.api.ServiceCall

trait AnotherService extends Service {

  final override def descriptor = {
    import Service._

    named("another-service")
      .withCalls(
        namedCall("/api/foo", foo)
      )
      .withAutoAcl(true)

  }

  def foo: ServiceCall[NotUsed, String]

}
