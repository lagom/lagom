/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl.services.headerfilters

package compose {

  import com.lightbend.lagom.scaladsl.api.transport.HeaderFilter
  import com.lightbend.lagom.scaladsl.api.transport.RequestHeader
  import com.lightbend.lagom.scaladsl.api.transport.ResponseHeader
  import com.lightbend.lagom.scaladsl.api.Service
  import com.lightbend.lagom.scaladsl.api.ServiceCall
  import org.slf4j.LoggerFactory

  //#verbose-filter
  class VerboseFilter(name: String) extends HeaderFilter {
    private val log = LoggerFactory.getLogger(getClass)

    def transformClientRequest(request: RequestHeader) = {
      log.debug(name + " - transforming Client Request")
      request
    }

    def transformServerRequest(request: RequestHeader) = {
      log.debug(name + " - transforming Server Request")
      request
    }

    def transformServerResponse(response: ResponseHeader, request: RequestHeader) = {

      log.debug(name + " - transforming Server Response")
      response
    }

    def transformClientResponse(response: ResponseHeader, request: RequestHeader) = {

      log.debug(name + " - transforming Client Response")
      response
    }
  }
  //#verbose-filter

  trait HelloService extends Service {
    def sayHello: ServiceCall[String, String]

    //#header-filter-composition
    def descriptor = {
      import Service._
      named("hello")
        .withCalls(
          call(sayHello)
        )
        .withHeaderFilter(
          HeaderFilter.composite(
            new VerboseFilter("Foo"),
            new VerboseFilter("Bar")
          )
        )
    }
    //#header-filter-composition
  }
}
