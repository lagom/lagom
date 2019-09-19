/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.projection

import akka.annotation.ApiMayChange
import akka.annotation.InternalApi;

/** Marker trait for projection serializers (for internal use, do not mark user code with this trait) */
@ApiMayChange
@InternalApi
trait ProjectionSerializable
