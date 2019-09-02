package com.example.hello.impl.readsides;

import com.lightbend.lagom.javadsl.persistence.ReadSide;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class StoppedProcessor extends AbstractReadSideProcessor {
    public static final String NAME = "StoppedProcessor";

    public static ConcurrentHashMap<String, String> greetings = new ConcurrentHashMap<>();

    @Inject
    public StoppedProcessor(ReadSide readSide) {
        super(NAME, readSide, greetings);
    }

}




