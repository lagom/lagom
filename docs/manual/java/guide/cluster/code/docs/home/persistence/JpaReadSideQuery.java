/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

// #imports

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaSession;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;
import java.util.concurrent.CompletionStage;
// #imports

public interface JpaReadSideQuery {

  interface BlogService {
    public ServiceCall<NotUsed, PSequence<PostSummary>> getPostSummaries();
  }

  // #service-impl
  public class BlogServiceImpl implements BlogService {

    private final JpaSession jpaSession;

    @Inject
    public BlogServiceImpl(JpaSession jpaSession) {
      this.jpaSession = jpaSession;
    }

    @Override
    public ServiceCall<NotUsed, PSequence<PostSummary>> getPostSummaries() {
      return request ->
          jpaSession.withTransaction(this::selectPostSummaries).thenApply(TreePVector::from);
    }

    private List<PostSummary> selectPostSummaries(EntityManager entityManager) {
      return entityManager
          .createQuery(
              "SELECT"
                  + " NEW com.example.PostSummary(s.id, s.title)"
                  + " FROM BlogSummaryJpaEntity s",
              PostSummary.class)
          .getResultList();
    }
  }
  // #service-impl
}
