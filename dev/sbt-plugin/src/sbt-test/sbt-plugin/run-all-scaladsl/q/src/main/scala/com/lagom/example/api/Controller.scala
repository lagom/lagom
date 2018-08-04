package com.lagom.example.api

import controllers.Assets
import org.slf4j.MDC
import play.api.mvc.Results._
import play.api.mvc._
import play.api.routing.sird._
import play.api.routing.{Router, SimpleRouter}
import play.api.{Environment, Mode}

final class Controller(
  Action: DefaultActionBuilder
) extends SimpleRouter {

  import com.lagom.example.api.Response._

  override def routes: Router.Routes = {
    case GET(p"/q") => Action { Ok(response) }
  }

}
