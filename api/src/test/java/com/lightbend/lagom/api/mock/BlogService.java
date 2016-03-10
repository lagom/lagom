/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.api.mock;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.paging.Page;
import com.lightbend.lagom.javadsl.api.transport.Method;
import com.lightbend.lagom.javadsl.api.deser.IdSerializers;

import java.util.Arrays;

import static com.lightbend.lagom.javadsl.api.Service.*;

public interface BlogService extends Service {

    class BlogId {
        private final String name;

        public BlogId(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BlogId blogId = (BlogId) o;

            return name.equals(blogId.name);

        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    class PostId {
        private final BlogId blogId;
        private final long id;

        public PostId(BlogId blogId, long id) {
            this.blogId = blogId;
            this.id = id;
        }

        public BlogId blogId() {
            return blogId;
        }

        public long id() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PostId postId = (PostId) o;

            if (id != postId.id) return false;
            return blogId.equals(postId.blogId);

        }

        @Override
        public int hashCode() {
            int result = blogId.hashCode();
            result = 31 * result + (int) (id ^ (id >>> 32));
            return result;
        }
    }

    class CommentId {
        private final PostId postId;
        private final int id;

        public CommentId(PostId postId, int id) {
            this.postId = postId;
            this.id = id;
        }

        public PostId postId() {
            return postId;
        }

        public int id() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CommentId commentId = (CommentId) o;

            if (id != commentId.id) return false;
            return postId.equals(commentId.postId);

        }

        @Override
        public int hashCode() {
            int result = postId.hashCode();
            result = 31 * result + id;
            return result;
        }
    }

    class PagedPosts {
        private final BlogId blogId;
        private final Page page;

        public PagedPosts(BlogId blogId, Page page) {
            this.blogId = blogId;
            this.page = page;
        }

        public BlogId blogId() {
            return blogId;
        }

        public Page page() {
            return page;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PagedPosts that = (PagedPosts) o;

            if (!blogId.equals(that.blogId)) return false;
            return page.equals(that.page);

        }

        @Override
        public int hashCode() {
            int result = blogId.hashCode();
            result = 31 * result + page.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "PagedPosts{" +
                    "blogId=" + blogId +
                    ", page=" + page +
                    '}';
        }
    }

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

    ServiceCall<BlogId, NotUsed, Blog> getBlog();
    ServiceCall<PagedPosts, NotUsed, NotUsed> getPosts();
    ServiceCall<PostId, NotUsed, NotUsed> getPost();
    ServiceCall<CommentId, NotUsed, NotUsed> getComment();

    @Override
    default Descriptor descriptor() {
        return named("/blogs").with(
                restCall(Method.GET, "/blogs/:blogId", getBlog()),
                restCall(Method.GET, "/blogs/:blogId/posts?pageNo&pageSize", getPosts()),
                restCall(Method.GET, "/blogs/:blogId/posts/:postId", getPost()),
                restCall(Method.GET, "/blogs/:blogId/posts/:postId/comments/:commentId", getComment())
                    // Demonstrate id serializer configured for a particular endpoint
                    .with(IdSerializers.create("CommentId", CommentId::new, comment -> Arrays.asList(comment.postId(), comment.id())))
        )
                // Demonstrate id serializers registered at the service level
                .with(BlogId.class, IdSerializers.create("BlogId", BlogId::new, BlogId::name))
                .with(PostId.class, IdSerializers.create("PostId", PostId::new, post -> Arrays.asList(post.blogId(), post.id())))
                .with(PagedPosts.class, IdSerializers.create("PagedPosts", PagedPosts::new, pp -> Arrays.asList(pp.blogId(), pp.page())));
    }
}
