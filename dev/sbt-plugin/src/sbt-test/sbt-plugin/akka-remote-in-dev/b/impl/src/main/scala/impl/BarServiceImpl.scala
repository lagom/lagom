/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package impl

import api.BarService
import com.lightbend.lagom.scaladsl.api.ServiceCall

import scala.concurrent.Future

class BarServiceImpl extends BarService {
  override def bar = ServiceCall { _ =>
    Future.successful("ack bar")
  }
}
