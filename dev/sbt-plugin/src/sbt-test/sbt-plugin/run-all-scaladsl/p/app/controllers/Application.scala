/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package controllers

import play.api.mvc._
import views._

class Application(controllerComponents: ControllerComponents) extends AbstractController(controllerComponents) {
  def index = Action {
    Ok(html.index())
  }
}
