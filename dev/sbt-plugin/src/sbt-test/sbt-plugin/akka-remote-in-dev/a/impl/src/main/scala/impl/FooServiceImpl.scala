/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package impl

import api.FooService
import com.lightbend.lagom.scaladsl.api.ServiceCall

import scala.concurrent.Future

class FooServiceImpl extends FooService {
  override def foo = ServiceCall { _ =>
    Future.successful("ack foo")
  }
}
