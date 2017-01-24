package docs.scaladsl.mb

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}


trait AnotherService extends Service {

  override final def descriptor = {
    import Service._

    named("another-service").withCalls(
      namedCall("/api/foo", foo)
    ).withAutoAcl(true)

  }

  def foo: ServiceCall[NotUsed, String]

}

