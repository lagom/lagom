package docs.mb

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}

/**
  *
  */
trait AnotherService extends Service {

  override final def descriptor = {
    import Service._

    named("another-service").withCalls(
      namedCall("/api/hello/:id", foo)
    ).withAutoAcl(true)

  }

  def foo: ServiceCall[NotUsed, NotUsed]

}

