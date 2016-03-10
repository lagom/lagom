/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.deser;

import java.util.Optional;

/**
 * An ID serializer is responsible for serializing and deserializing IDs from URL paths.
 *
 * A path is specified in the framework using a String like <code>/blog/:blogId/post/:postId</code>.
 * The <code>blogId</code> and <code>postId</code> parts of the path are extracted, and passed in order in a list of
 * parameters to the IdSerializer.  When creating a path, the IdSerializer takes the id object, and turns it back into
 * a list of path parameters.
 *
 * @param <Id> The type of the ID.
 */
public interface IdSerializer<Id> {

    /**
     * Serialize the given <code>id</code> into a list of path parameters.
     *
     * @param id The id to serialize.
     * @return The RawId.
     */
    RawId serialize(Id id);

    /**
     * Deserialize the <code>rawId</code> into an ID.
     *
     * @return The deserialized ID.
     */
    Id deserialize(RawId rawId);

    /**
     * A hint of the number of path parameters that this id serializer serializes.
     *
     * This is used by id serializers that compose with other ID serializers, so that a parent serializer can know
     * how many parameters its children extract, that way it can modify the raw ID it passes to them in order for them
     * to deserialize just the part of the ID that they are interested in.
     *
     * If this returns empty, it means this ID serializer cannot be composed with other IdSerializers that extract
     * path parameters.
     *
     * @return The number of path parameters that this ID serializer extracts.
     */
    default Optional<Integer> numPathParamsHint() {
        return Optional.empty();
    }
}
