/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

// #imports
import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.jdbc.JdbcSession;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import javax.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
// #imports

public interface JdbcReadSideQuery {

  interface BlogService {
    public ServiceCall<NotUsed, PSequence<PostSummary>> getPostSummaries();
  }

  // #service-impl
  public class BlogServiceImpl implements BlogService {

    private final JdbcSession jdbcSession;

    @Inject
    public BlogServiceImpl(JdbcSession jdbcSession) {
      this.jdbcSession = jdbcSession;
    }

    @Override
    public ServiceCall<NotUsed, PSequence<PostSummary>> getPostSummaries() {
      return request -> {
        return jdbcSession.withConnection(
            connection -> {
              try (PreparedStatement ps =
                  connection.prepareStatement("SELECT id, title FROM blogsummary")) {
                try (ResultSet rs = ps.executeQuery()) {
                  PSequence<PostSummary> summaries = TreePVector.empty();

                  while (rs.next()) {
                    summaries =
                        summaries.plus(new PostSummary(rs.getString("id"), rs.getString("title")));
                  }

                  return summaries;
                }
              }
            });
      };
    }
  }
  // #service-impl

}
