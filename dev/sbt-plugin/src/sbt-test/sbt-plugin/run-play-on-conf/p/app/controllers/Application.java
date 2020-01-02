/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package controllers;

import play.*;
import play.mvc.*;

import views.html.*;

public class Application extends Controller {
    public Result index() {
        return ok(index.render());
    }
}
