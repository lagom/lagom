package com.example.hello.impl.readsides;

import akka.Done;
import akka.japi.Pair;
import akka.stream.javadsl.Flow;
import com.example.hello.impl.HelloEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.Offset;
import com.lightbend.lagom.javadsl.persistence.ReadSide;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import org.pcollections.PSequence;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;


class AbstractReadSideProcessor extends ReadSideProcessor<HelloEvent> {

    private String processorName;
    private final ReadSide readSide;
    private ConcurrentHashMap<String, String> greetings;

    AbstractReadSideProcessor(String processorName, ReadSide readSide, ConcurrentHashMap<String, String> greetings) {
        this.processorName = processorName;
        this.readSide = readSide;
        this.greetings = greetings;
    }

    private final CompletionStage<Done> doneCompletableFuture = CompletableFuture.completedFuture(Done.getInstance());

    @Override
    public PSequence<AggregateEventTag<HelloEvent>> aggregateTags() {
        return HelloEvent.TAG.allTags();
    }

    public String getLastMessage(String id) {
        return greetings.getOrDefault(id, "default-projected-message");
    }

    @Override
    public String readSideName() {
        return processorName;
    }

    @Override
    public ReadSideHandler<HelloEvent> buildHandler() {

        return new ReadSideHandler<HelloEvent>() {


            @Override
            public CompletionStage<Done> globalPrepare() {
                return doneCompletableFuture;
            }

            @Override
            public CompletionStage<Offset> prepare(AggregateEventTag<HelloEvent> tag) {
                return CompletableFuture.completedFuture(Offset.NONE);
            }

            @Override
            public Flow<Pair<HelloEvent, Offset>, Done, ?> handle() {
                return Flow.<Pair<HelloEvent, Offset>>create()
                    .mapAsync(
                        1,
                        eventAndOffset -> {
                            HelloEvent event = eventAndOffset.first();
                            if (event instanceof HelloEvent.GreetingMessageChanged) {
                                HelloEvent.GreetingMessageChanged changed = (HelloEvent.GreetingMessageChanged) eventAndOffset.first();
                                greetings.put(changed.name, changed.message);
                            }
                            return doneCompletableFuture;
                        }
                    );
            }
        };
    }


}
