package controllers;

import play.mvc.Controller;
import play.mvc.Result;

public class Application extends Controller {

  public Result index() {
    return ok(views.html.index.render());
  }

  public Result userStream(String userId) {
    return ok(views.html.index.render());
  }

  public Result circuitBreaker() {
    return ok(views.html.circuitbreaker.render());
  }

}
