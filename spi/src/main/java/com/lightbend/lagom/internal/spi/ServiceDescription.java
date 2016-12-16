/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.spi;

import java.util.List;

public interface ServiceDescription {
    String name();
    List<ServiceAcl> acls();
}
