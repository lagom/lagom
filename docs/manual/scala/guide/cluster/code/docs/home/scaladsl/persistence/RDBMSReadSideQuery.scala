package docs.home.scaladsl.persistence

//#imports
import scala.collection.immutable
import scala.collection.immutable.VectorBuilder
import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.jdbc.JdbcSession

//#imports

trait RDBMSReadSideQuery {

  trait BlogService extends Service {
    def getPostSummaries(): ServiceCall[NotUsed, immutable.IndexedSeq[PostSummary]]

    override def descriptor = ???
  }

  //#service-impl
  class BlogServiceImpl(jdbcSession: JdbcSession) extends BlogService {

    override def getPostSummaries() = ServiceCall { request =>
      jdbcSession.withConnection { connection =>
        val rs = connection.prepareStatement("SELECT id, title FROM blogsummary")
          .executeQuery()
        val summaries = new VectorBuilder[PostSummary]
        while (rs.next()) {
          summaries +=
            PostSummary(rs.getString("id"), rs.getString("title"))
        }
        summaries.result()
      }
    }
    //#service-impl

  }
}
