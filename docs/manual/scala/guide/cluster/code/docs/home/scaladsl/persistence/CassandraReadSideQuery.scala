/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

//#imports
import scala.concurrent.Future
import akka.NotUsed
import akka.stream.scaladsl.Source
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraSession

//#imports

trait CassandraReadSideQuery {

  trait BlogService extends Service {
    def getPostSummaries(): ServiceCall[NotUsed, Source[PostSummary, _]]

    override def descriptor = ???
  }

  //#service-impl
  class BlogServiceImpl(cassandraSession: CassandraSession) extends BlogService {

    override def getPostSummaries() = ServiceCall { request =>
      val response: Source[PostSummary, NotUsed] =
        cassandraSession
          .select("SELECT id, title FROM blogsummary")
          .map(row => PostSummary(row.getString("id"), row.getString("title")))
      Future.successful(response)
    }
  }
  //#service-impl

}
