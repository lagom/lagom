/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package controllers

import play.api._
import play.api.mvc._
import scala.collection.JavaConverters._

import javax.inject.Inject

class Application @Inject()(
  env: Environment,
  configuration: Configuration,
  val controllerComponents: ControllerComponents
) extends BaseController {

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def config = Action {
    Ok(configuration.underlying.getString("some.config"))
  }

  def count = Action {
    val num = env.resource("application.conf").toSeq.size
    Ok(num.toString)
  }
}
