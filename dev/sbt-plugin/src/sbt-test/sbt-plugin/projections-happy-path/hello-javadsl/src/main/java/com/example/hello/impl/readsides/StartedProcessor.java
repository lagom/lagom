/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.hello.impl.readsides;

import com.lightbend.lagom.javadsl.persistence.ReadSide;

import javax.inject.Inject;
import java.util.concurrent.ConcurrentHashMap;

public class StartedProcessor extends AbstractReadSideProcessor {
    public static final String NAME = "StartedProcessor";

    private static ConcurrentHashMap<String, String> greetings = new ConcurrentHashMap<>();
    @Inject
    public StartedProcessor(ReadSide readSide) {
        super(NAME, readSide, greetings);
    }
}
