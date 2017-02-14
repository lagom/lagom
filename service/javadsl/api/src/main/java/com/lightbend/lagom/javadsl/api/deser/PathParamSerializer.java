/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.deser;

import org.pcollections.PSequence;

/**
 * A path param serializer is responsible for serializing and deserializing parameters that are extracted from and
 * formatted into paths.
 *
 * When used in URLs, a path param serializer is used both for path segments as well as query string parameters.  It is
 * expected that the serializer will consume and return singleton sequences for path segments, but may return 0 to many
 * values for query string parameters.
 *
 * @param <Param> The type of the path parameter.
 */
public interface PathParamSerializer<Param> {

    /**
     * Serialize the given <code>parameter</code> into path parameters.
     *
     * @param parameter The parameter to serialize.
     * @return The parameters.
     */
    PSequence<String> serialize(Param parameter);

    /**
     * Deserialize the <code>parameters</code> into a deserialized parameter.
     *
     * @return The deserialized parameter.
     */
    Param deserialize(PSequence<String> parameters);
}
