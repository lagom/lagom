/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.spi;

import java.util.Optional;

public interface ServiceAcl {
    Optional<String> method();
    Optional<String> pathPattern();
}
