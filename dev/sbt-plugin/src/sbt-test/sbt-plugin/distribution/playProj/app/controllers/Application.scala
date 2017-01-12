/*
 * Copyright (C) 2009-2017 Typesafe Inc. <https://www.typesafe.com>
 */
package controllers

import play.api._
import play.api.mvc._
import scala.collection.JavaConverters._

import javax.inject.Inject

class Application @Inject() (app: play.api.Application, configuration: Configuration) extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def config = Action {
    Ok(configuration.underlying.getString("some.config"))
  }

  def count = Action {
    val num = app.resource("application.conf").toSeq.size
    Ok(num.toString)
  }
}
