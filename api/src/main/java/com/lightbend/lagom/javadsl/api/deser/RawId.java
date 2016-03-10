/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.deser;

import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import java.util.Optional;

/**
 * A raw ID.
 *
 * A raw ID consists of an ordered list of path parameters, which are ordered as they appear in the path, and a map of
 * query parameters.
 *
 * Raw ids are extracted fram the path according to a path spec.  For example, the following path spec:
 *
 * <pre>
 *     /blog/:blogId/post/:postId/comments?pageNo&amp;pageSize
 * </pre>
 *
 * With the following path:
 *
 * <pre>
 *     /blog/123/post/456/comments?pageNo=2
 * </pre>
 *
 * Will extract a raw ID with two path parameters, the first parameter being named <code>blogId</code> with a value of
 * <code>123</code>, the second value being named <code>postId</code> with a value of <code>456</code>.  It will also
 * have two query string parameters, one named <code>pageNo</code> with a value of <code>2</code>, the other named
 * <code>pageSize</code> with an empty value.
 *
 * Raw ids are what {@link IdSerializer} uses to convert the extracted path information into an Id type, and back.
 */
public final class RawId {

    /**
     * An empty RawId.
     */
    public static final RawId EMPTY = new RawId(TreePVector.<PathParam>empty(), HashTreePMap.empty());

    /**
     * A path parameter.
     *
     * A path parameter is an optional name, and a value.  The name is optional because it may not be known at
     * serialization time.  It is a hint that may be useful when deserializing, at which time the name will be the name
     * of the parameter in the path spec.  When serializing, the final name will be ignored - the order of the
     * parameters is used to format the path spec, not the names themselves.
     */
    public static final class PathParam {
        private final Optional<String> name;
        private final String value;

        private PathParam(Optional<String> name, String value) {
            this.name = name;
            this.value = value;
        }

        /**
         * Get the name of the path parameter.
         *
         * @return The name of the path parameter.
         */
        public Optional<String> name() {
            return name;
        }

        /**
         * Get the value of the path parameter.
         *
         * @return The value of the path parameter.
         */
        public String value() {
            return value;
        }

        /**
         * Create a path parameter of the given value.
         *
         * @param value The value.
         * @return The path parameter.
         */
        public static PathParam of(String value) {
            return new PathParam(Optional.empty(), value);
        }

        /**
         * Create a path parameter with the given name and value.
         *
         * @param name The name.
         * @param value The value.
         * @return The path parameter.
         */
        public static PathParam of(String name, String value) {
            return new PathParam(Optional.of(name), value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            PathParam pathParam = (PathParam) o;

            return value.equals(pathParam.value);

        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return name + "=" + value;
        }
    }

    private final PSequence<PathParam> pathParams;
    private final PMap<String, PSequence<String>> queryParams;

    private RawId(PSequence<PathParam> pathParams, PMap<String, PSequence<String>> queryParams) {
        this.pathParams = pathParams;
        this.queryParams = queryParams;
    }

    /**
     * Get the path parameters for the ID.
     *
     * The path parameters are in the order that they appear in the path spec.
     *
     * @return The path parameters.
     */
    public PSequence<PathParam> pathParams() {
        return pathParams;
    }

    /**
     * Get the query parameters for the ID.
     *
     * @return The query parameters.
     */
    public PMap<String, PSequence<String>> queryParams() {
        return queryParams;
    }

    /**
     * Get the path parameter with the given <code>name</code>.
     *
     * @param name The name of the path parameter.
     * @return The path parameter, if it exists, otherwise empty.
     */
    public Optional<String> pathParam(String name) {
        return pathParams.stream()
                .filter(p -> p.name.map(name::equals).orElse(false))
                .findFirst()
                .map(PathParam::value);
    }

    /**
     * Get a query string parameter with the given <code>name</code>.
     *
     * @param name The name of the query parameter.
     * @return The query string parameter, if it can be found, otherwise empty.
     */
    public Optional<String> queryParam(String name) {
        return Optional.ofNullable(queryParams.get(name)).flatMap(p -> {
                if (p.isEmpty()) {
                    return Optional.empty();
                } else {
                    return Optional.of(p.get(0));
                }
        });
    }

    /**
     * Add the given query parameter.
     *
     * @param name The name of the query parameter.
     * @param values The values for the query parameter.
     * @return A new RawId with the added query parameter.
     */
    public RawId withQueryParam(String name, PSequence<String> values) {
        return new RawId(pathParams, queryParams.plus(name, values));
    }

    /**
     * Add the given query parameter.
     *
     * @param name The name of the query parameter.
     * @param value The value for the query parameter.
     * @return A new RawId with the added query parameter.
     */
    public RawId withQueryParam(String name, Optional<String> value) {
        return new RawId(pathParams, queryParams.plus(name,
                value.map(TreePVector::singleton).orElse(TreePVector.empty())));
    }

    /**
     * Add the given query parameter.
     *
     * @param name The name of the query parameter.
     * @param value The value of the query parameter.
     * @return A new RawId with the added query parameter.
     */
    public RawId withQueryParam(String name, String value) {
        return withQueryParam(name, TreePVector.singleton(value));
    }

    /**
     * Add the given path parameter.
     *
     * @param name The name of the path parameter.
     * @param value The value of the path parameter.
     * @return A new RawId with the added path parameter.
     */
    public RawId withPathParam(String name, String value) {
        return new RawId(pathParams.plus(PathParam.of(name, value)), queryParams);
    }

    /**
     * Add the given path parameter value.
     *
     * @param value The value of the path parameter.
     * @return A new RawId with the added path parameter.
     */
    public RawId withPathValue(String value) {
        return new RawId(pathParams.plus(PathParam.of(value)), queryParams);
    }

    /**
     * Create a new raw ID from the given path parameters and query parameters.
     *
     * @param pathParams The path parameters.
     * @param queryParams The query parameters.
     * @return The raw ID.
     */
    public static RawId of(PSequence<PathParam> pathParams, PMap<String, PSequence<String>> queryParams) {
        return new RawId(pathParams, queryParams);
    }

    /**
     * Create a new raw ID from the given path parameters and empty query parameters.
     *
     * @param pathParams The path parameters.
     * @return The raw ID.
     */
    public static RawId of(PSequence<PathParam> pathParams) {
        return new RawId(pathParams, HashTreePMap.empty());
    }

    /**
     * Create a new raw ID from the given query parameters and empty path parameters.
     *
     * @param queryParams The query parameters.
     * @return The raw ID.
     */
    public static RawId of(PMap<String, PSequence<String>> queryParams) {
        return new RawId(TreePVector.empty(), queryParams);
    }

    /**
     * Create a new raw ID from the given path values and empty query parameters.
     *
     * @param values The path parameter values.
     * @return The raw ID.
     */
    public static RawId fromPathValues(PSequence<String> values) {
        PSequence<PathParam> params = TreePVector.empty();
        for (String value: values) {
            params = params.plus(PathParam.of(value));
        }
        return of(params);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RawId rawId = (RawId) o;

        if (!pathParams.equals(rawId.pathParams)) return false;
        return queryParams.equals(rawId.queryParams);

    }

    @Override
    public int hashCode() {
        int result = pathParams.hashCode();
        result = 31 * result + queryParams.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "RawId{" +
                "pathParams=" + pathParams +
                ", queryParams=" + queryParams +
                '}';
    }
}
