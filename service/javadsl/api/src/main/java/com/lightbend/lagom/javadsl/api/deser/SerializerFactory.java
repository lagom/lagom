/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.deser;

import com.lightbend.lagom.internal.javadsl.api.JacksonPlaceholderSerializerFactory$;

import java.lang.reflect.Type;

/**
 * A serializer factory is responsible for constructing serializers for types.
 *
 * It is used when no serializer is explicitly defined for a message type, either specific to the endpoint, or for
 * a descriptor.
 */
public interface SerializerFactory {
    /**
     * Get a message serializer for the given type.
     *
     * @param type The type to get a message serializer for.
     * @return The message serializer.
     */
    <MessageEntity> MessageSerializer<MessageEntity, ?> messageSerializerFor(Type type);

    /**
     * The default Jackson serializer factory.
     *
     * This is only a placeholder, the framework will provide the actual implementation at runtime.
     */
    SerializerFactory JACKSON = JacksonPlaceholderSerializerFactory$.MODULE$;

    /**
     * The default serializer factory.
     */
    SerializerFactory DEFAULT = JACKSON;
}
