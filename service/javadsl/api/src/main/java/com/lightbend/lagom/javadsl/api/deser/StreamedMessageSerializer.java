/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.deser;

import akka.stream.javadsl.Source;
import akka.util.ByteString;

/**
 * A streamed message serializer, for streams of messages.
 */
public interface StreamedMessageSerializer<MessageEntity> extends MessageSerializer<Source<MessageEntity, ?>,
        Source<ByteString, ?>> {

    @Override
    default boolean isStreamed() {
        return true;
    }
}
