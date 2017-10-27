/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.api.mock;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface BlogService extends Service {

    class Blog {
        private final String name;

        public Blog(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Blog blog = (Blog) o;

            return name.equals(blog.name);

        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    ServiceCall<NotUsed, Blog> getBlog(String blogId);
    ServiceCall<NotUsed, NotUsed> getPosts(String blogId, Optional<Integer> pageNo, Optional<Integer> pageSize);
    ServiceCall<NotUsed, NotUsed> getPost(String blogId, long postId);
    ServiceCall<NotUsed, NotUsed> getComment(String blogId, long postId, long commentId);
    ServiceCall<NotUsed, NotUsed> getCommentRepeatList(String blogId, List<String> repeat);
    ServiceCall<NotUsed, NotUsed> getCommentRepeatCollection(String blogId, Collection<String> repeat);
    ServiceCall<NotUsed, NotUsed> getCommentRepeatSet(String blogId, Set<String> repeat);

    @Override
    default Descriptor descriptor() {
        return named("/blogs").withCalls(
                restCall(Method.GET, "/blogs/:blogId", this::getBlog),
                restCall(Method.GET, "/blogs/:blogId/posts?pageNo&pageSize", this::getPosts),
                restCall(Method.GET, "/blogs/:blogId/posts/:postId", this::getPost),
                restCall(Method.GET, "/blogs/:blogId/posts/:postId/comments/:commentId", this::getComment),
                restCall(Method.GET, "/blogs/:blogId/posts/repeatCol?repeat", this::getCommentRepeatCollection),
                restCall(Method.GET, "/blogs/:blogId/posts/repeatList?repeat", this::getCommentRepeatList),
                restCall(Method.GET, "/blogs/:blogId/posts/repeatSet?repeat", this::getCommentRepeatSet)
        );
    }
}
