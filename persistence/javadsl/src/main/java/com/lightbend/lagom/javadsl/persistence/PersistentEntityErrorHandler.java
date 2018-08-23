/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence;

import java.util.concurrent.CompletionStage;

public interface PersistentEntityErrorHandler {
    <Reply,Cmd extends Object & PersistentEntity.ReplyType<Reply>> CompletionStage<Reply> handleAskFailure(Throwable failure, Cmd command);
}

