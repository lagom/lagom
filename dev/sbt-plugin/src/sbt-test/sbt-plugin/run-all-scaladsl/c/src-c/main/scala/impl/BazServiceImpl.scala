/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package impl

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.ServiceCall
import scala.concurrent.Future
import api.BazService

class BazServiceImpl extends BazService {
  override def baz = ServiceCall { _ =>
    Future.successful("ack baz")
  }
}
