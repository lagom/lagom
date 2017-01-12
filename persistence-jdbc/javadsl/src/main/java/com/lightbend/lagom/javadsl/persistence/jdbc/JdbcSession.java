/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletionStage;

public interface JdbcSession {

    /**
     * Execute the given function with a connection.
     *
     * This will execute the callback in a thread pool that is specifically designed for use with JDBC calls.
     *
     * @param block The block to execute.
     * @return A future of the result.
     */
    <T> CompletionStage<T> withConnection(ConnectionFunction<T> block);

    /**
     * Execute the given function in a transaction.
     *
     * This will execute the callback in a thread pool that is specifically designed for use with JDBC calls.
     *
     * @param block The block to execute.
     * @return A future of the result.
     */
    <T> CompletionStage<T> withTransaction(ConnectionFunction<T> block);

    /**
     * SAM for using a connection.
     */
    @FunctionalInterface
    interface ConnectionFunction<T> {

        /**
         * Execute this function with the connection.
         *
         * @param connection The connection
         */
        T apply(Connection connection) throws SQLException;
    }
}
