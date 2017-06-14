package controllers

import play.api.mvc._
import views._

class Application extends Controller {
  def index = Action {
    Ok(html.index())
  }
}
