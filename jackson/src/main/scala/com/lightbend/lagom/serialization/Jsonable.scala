/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.serialization

// JsonSerializable would be a nice name, but that is already defined in Jackson,
// so we are avoiding wrong import mistake by using another name

/**
 * Marker interface for messages that are serialized as JSON.
 *
 * It extends java.io.Serializable to give it higher priority than JavaSerializer
 * in Akka in case message class implements both interfaces.
 */
trait Jsonable extends Serializable

/**
 * The serializer will compress the payload if the message class implements this
 * marker interface and the payload is larger than the configured
 * `compress-larger-than` value.
 */
trait CompressedJsonable extends Jsonable
