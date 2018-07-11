package controllers

import play.api.mvc._
import views._

class Application(controllerComponents: ControllerComponents) extends AbstractController(controllerComponents) {
  def index = Action {
    Ok(html.index())
  }
}
