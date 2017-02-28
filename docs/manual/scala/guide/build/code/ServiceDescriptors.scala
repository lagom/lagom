package docs.scaladsl.services

package buildhelloservice {

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


