/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class DefaultPersistentEntityErrorHandler implements PersistentEntityErrorHandler {

    static <Reply> CompletionStage<Reply> asFailedReply(Throwable failure){
        CompletableFuture<Reply> failed = new CompletableFuture<>();
        failed.completeExceptionally(failure);
        return failed;
    }


    @Override
    public <Reply, Cmd extends Object & PersistentEntity.ReplyType<Reply>> CompletionStage<Reply> handleAskFailure(Throwable failure, Cmd command) {
        return asFailedReply(failure);
    }


    private DefaultPersistentEntityErrorHandler(){

    }

    public static final PersistentEntityErrorHandler INSTANCE = new DefaultPersistentEntityErrorHandler();
}
