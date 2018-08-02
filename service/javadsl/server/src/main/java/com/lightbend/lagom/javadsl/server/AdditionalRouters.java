/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.javadsl.server;

import play.routing.Router;

import java.util.Collections;
import java.util.List;

public interface AdditionalRouters {
    public static final AdditionalRouters EMPTY = new AdditionalRouters() {
        @Override
        public List<Router> getRouters() {
            return Collections.emptyList();
        }
    };

    List<Router> getRouters();
}

