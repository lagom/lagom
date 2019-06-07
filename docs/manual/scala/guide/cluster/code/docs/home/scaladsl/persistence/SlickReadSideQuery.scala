/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

import akka.NotUsed
//#imports
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.api.ServiceCall
import slick.jdbc.JdbcBackend.Database
//#imports
import docs.home.scaladsl.persistence.SlickRepos.Initial.PostSummaryRepository

trait SlickReadSideQuery {

  trait BlogService extends Service {
    def getPostSummaries(): ServiceCall[NotUsed, Seq[PostSummary]]
    override def descriptor = ???
  }

  //#service-impl
  class BlogServiceImpl(db: Database, val postSummaryRepo: PostSummaryRepository) extends BlogService {

    override def getPostSummaries() = ServiceCall { request =>
      db.run(postSummaryRepo.selectPostSummaries())
    }
    //#service-impl

  }
}
