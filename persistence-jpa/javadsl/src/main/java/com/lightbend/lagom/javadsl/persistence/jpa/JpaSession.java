/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jpa;

import javax.persistence.EntityManager;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public interface JpaSession {
    <T> CompletionStage<T> withEntityManager(Function<EntityManager, T> block);
    <T> CompletionStage<T> withTransaction(Function<EntityManager, T> block);
}
