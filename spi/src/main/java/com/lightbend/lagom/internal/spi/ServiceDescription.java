/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.spi;

import java.util.List;

public interface ServiceDescription {
    String name();
    List<ServiceAcl> acls();
}
