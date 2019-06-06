/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

import scala.concurrent.Future
import akka.NotUsed
import akka.stream.scaladsl.Source
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.api.ServiceCall

trait BlogService extends Service {
  def getPostSummaries(): ServiceCall[NotUsed, Source[PostSummary, _]]

  override def descriptor = ???
}
