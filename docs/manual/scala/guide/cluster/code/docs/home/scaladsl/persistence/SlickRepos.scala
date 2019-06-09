/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.persistence

import akka.Done
import slick.dbio.DBIOAction
import slick.dbio.Effect
import slick.dbio.Effect.All
import slick.sql.FixedSqlAction

object SlickRepos {

  object Initial {

    // #slick-mapping-initial
    import slick.jdbc.H2Profile.api._

    class PostSummaryRepository {

      class PostSummaryTable(tag: Tag) extends Table[PostSummary](tag, "post_summary") {

        def *      = (postId, title) <> (PostSummary.tupled, PostSummary.unapply)
        def postId = column[String]("post_id", O.PrimaryKey)
        def title  = column[String]("title")
      }

      val postSummaries = TableQuery[PostSummaryTable]

      def selectPostSummaries() = postSummaries.result
    }

    // #slick-mapping-initial
  }

  object WithCreateTable {

    // need to import it first to make table compile
    import scala.concurrent.ExecutionContext.Implicits.global
    import slick.jdbc.H2Profile.api._

    class PostSummaryTable(tag: Tag) extends Table[PostSummary](tag, "post_summary") {

      def *      = (postId, title) <> (PostSummary.tupled, PostSummary.unapply)
      def postId = column[String]("post_id", O.PrimaryKey)
      def title  = column[String]("title")
    }

    // import again, for documentation purpose
    // #slick-mapping-schema
    import scala.concurrent.ExecutionContext.Implicits.global
    import slick.jdbc.H2Profile.api._

    class PostSummaryRepository {
      // table mapping omitted for conciseness
      val postSummaries = TableQuery[PostSummaryTable]

      def createTable = postSummaries.schema.createIfNotExists
    }
    // #slick-mapping-schema
  }

  object Full {

    import scala.concurrent.ExecutionContext.Implicits.global
    import slick.jdbc.H2Profile.api._

    class PostSummaryRepository {
      class PostSummaryTable(tag: Tag) extends Table[PostSummary](tag, "post_summary") {

        def *      = (postId, title) <> (PostSummary.tupled, PostSummary.unapply)
        def postId = column[String]("post_id", O.PrimaryKey)
        def title  = column[String]("title")
      }

      val postSummaries = TableQuery[PostSummaryTable]

      def createTable = postSummaries.schema.createIfNotExists

      // #insert-or-update
      /* added to PostSummaryRepository to insert or update Post Summaries */
      def save(postSummary: PostSummary) = {
        postSummaries.insertOrUpdate(postSummary).map(_ => Done)
      }
      // #insert-or-update
    }
  }
}
