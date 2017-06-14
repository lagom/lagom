package api

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api._
import com.lightbend.lagom.scaladsl.api.transport.Method

trait FooService extends Service {

  def foo: ServiceCall[NotUsed, String]

  override def descriptor = {
    import Service._
    named("a").withCalls(
      restCall(Method.GET, "/foo", foo)
    ).withAutoAcl(true)
  }
}
