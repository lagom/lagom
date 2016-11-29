package api

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api._
import com.lightbend.lagom.scaladsl.api.transport.Method

trait BarService extends Service {

  def bar: ServiceCall[NotUsed, String]

  override def descriptor = {
    import Service._
    named("b").withCalls(
      restCall(Method.GET, "/bar", bar)
    ).withAutoAcl(true)
  }
}