package api

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api._
import com.lightbend.lagom.scaladsl.api.transport.Method

trait BazService extends Service {

  def baz: ServiceCall[NotUsed, String]

  override def descriptor = {
    import Service._
    named("c").withCalls(
      restCall(Method.GET, "/baz", baz)
    ).withAutoAcl(true)
  }
}
