/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.deser;

import akka.util.ByteString;

/**
 * A strict message serializer, for messages that fit and are worked with strictly in memory.
 *
 * Strict message serializers differ from streamed serializers, in that they work directly with `ByteString`, rather
 * than an Akka streams `Source`.
 */
public interface StrictMessageSerializer<MessageEntity> extends MessageSerializer<MessageEntity, ByteString> {
}
