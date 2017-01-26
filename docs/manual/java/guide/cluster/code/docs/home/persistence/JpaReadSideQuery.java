/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package docs.home.persistence;

//#imports

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.jpa.JpaSession;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.CompletionStage;
//#imports

public interface JpaReadSideQuery {

    interface BlogService {
        public ServiceCall<NotUsed, PSequence<PostSummary>> getPostSummaries();
    }

    //#service-impl
    public class BlogServiceImpl implements BlogService {
        private static final String SELECT_POST_SUMMARIES =
                "SELECT NEW " +
                        "docs.home.persistence.PostSummary(s.id, s.title) " +
                        "FROM BlogSummaryJpaEntity s";

        private final JpaSession jpaSession;

        @Inject
        public BlogServiceImpl(JpaSession jpaSession) {
            this.jpaSession = jpaSession;
        }

        @Override
        public ServiceCall<NotUsed, PSequence<PostSummary>> getPostSummaries() {
            return request -> {
                CompletionStage<List<PostSummary>> results = jpaSession.withTransaction(entityManager ->
                        entityManager.createQuery(
                                SELECT_POST_SUMMARIES,
                                PostSummary.class
                        ).getResultList()
                );
                return results.thenApply(TreePVector::from);
            };
        }
    }
    //#service-impl
}
