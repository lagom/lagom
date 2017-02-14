/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra

import java.util.concurrent.CompletionStage
import java.util.{ Optional, List => JList }
import javax.inject.{ Inject, Singleton }

import akka.actor.ActorSystem
import akka.persistence.cassandra.session.CassandraSessionSettings
import akka.persistence.cassandra.session.scaladsl.{ CassandraSession => AkkaScalaCassandraSession }
import akka.persistence.cassandra.session.javadsl.{ CassandraSession => AkkaJavaCassandraSession }
import akka.stream.javadsl
import akka.{ Done, NotUsed }
import com.datastax.driver.core._
import com.lightbend.lagom.internal.persistence.cassandra.CassandraReadSideSessionProvider

import scala.annotation.varargs
import scala.concurrent.ExecutionContext

/**
 * Data Access Object for Cassandra. The statements are expressed in
 * <a href="http://docs.datastax.com/en/cql/3.3/cql/cqlIntro.html">Cassandra Query Language</a>
 * (CQL) syntax.
 *
 * The configured keyspace is automatically created if it doesn't already exists. The keyspace
 * is also set as the current keyspace, i.e. it doesn't have to be qualified in the statements.
 *
 * All methods are non-blocking.
 */
@Singleton
final class CassandraSession(system: ActorSystem, settings: CassandraSessionSettings, executionContext: ExecutionContext) {

  @Inject
  def this(system: ActorSystem) =
    this(
      system,
      settings = CassandraSessionSettings(system.settings.config.getConfig(
        "lagom.persistence.read-side.cassandra"
      )),
      executionContext = system.dispatchers.lookup(system.settings.config.getString(
        "lagom.persistence.read-side.use-dispatcher"
      ))
    )

  /**
   * Internal API
   */
  private[lagom] val scalaDelegate: AkkaScalaCassandraSession = CassandraReadSideSessionProvider(system, settings, executionContext)
  private val delegate = new AkkaJavaCassandraSession(scalaDelegate)

  /**
   * The `Session` of the underlying
   * <a href="http://datastax.github.io/java-driver/">Datastax Java Driver</a>.
   * Can be used in case you need to do something that is not provided by the
   * API exposed by this class. Be careful to not use blocking calls.
   */
  def underlying(): CompletionStage[Session] =
    delegate.underlying()

  /**
   * See <a href="http://docs.datastax.com/en/cql/3.3/cql/cql_using/useCreateTableTOC.html">Creating a table</a>.
   *
   * The returned `CompletionStage` is completed when the table has been created,
   * or if the statement fails.
   */
  def executeCreateTable(stmt: String): CompletionStage[Done] =
    delegate.executeCreateTable(stmt)

  /**
   * Create a `PreparedStatement` that can be bound and used in
   * `executeWrite` or `select` multiple times.
   */
  def prepare(stmt: String): CompletionStage[PreparedStatement] =
    delegate.prepare(stmt)

  /**
   * Execute several statements in a batch. First you must [[#prepare]] the
   * statements and bind its parameters.
   *
   * See <a href="http://docs.datastax.com/en/cql/3.3/cql/cql_using/useBatchTOC.html">Batching data insertion and updates</a>.
   *
   * The configured write consistency level is used if a specific consistency
   * level has not been set on the `BatchStatement`.
   *
   * The returned `CompletionStage` is completed when the batch has been
   * successfully executed, or if it fails.
   */
  def executeWriteBatch(batch: BatchStatement): CompletionStage[Done] =
    delegate.executeWriteBatch(batch)

  /**
   * Execute one statement. First you must [[#prepare]] the
   * statement and bind its parameters.
   *
   * See <a href="http://docs.datastax.com/en/cql/3.3/cql/cql_using/useInsertDataTOC.html">Inserting and updating data</a>.
   *
   * The configured write consistency level is used if a specific consistency
   * level has not been set on the `Statement`.
   *
   * The returned `CompletionStage` is completed when the statement has been
   * successfully executed, or if it fails.
   */
  def executeWrite(stmt: Statement): CompletionStage[Done] =
    delegate.executeWrite(stmt)

  /**
   * Prepare, bind and execute one statement in one go.
   *
   * See <a href="http://docs.datastax.com/en/cql/3.3/cql/cql_using/useInsertDataTOC.html">Inserting and updating data</a>.
   *
   * The configured write consistency level is used.
   *
   * The returned `CompletionStage` is completed when the statement has been
   * successfully executed, or if it fails.
   */
  @varargs
  def executeWrite(stmt: String, bindValues: AnyRef*): CompletionStage[Done] =
    delegate.executeWrite(stmt, bindValues: _*)

  /**
   * Execute a select statement. First you must [[#prepare]] the
   * statement and bind its parameters.
   *
   * See <a href="http://docs.datastax.com/en/cql/3.3/cql/cql_using/useQueryDataTOC.html">Querying tables</a>.
   *
   * The configured read consistency level is used if a specific consistency
   * level has not been set on the `Statement`.
   *
   * You can return this `Source` as a response in a `ServiceCall`
   * and the elements will be streamed to the client.
   * Otherwise you have to connect a `Sink` that consumes the messages from
   * this `Source` and then `run` the stream.
   */
  def select(stmt: Statement): javadsl.Source[Row, NotUsed] =
    delegate.select(stmt)

  /**
   * Prepare, bind and execute a select statement in one go.
   *
   * See <a href="http://docs.datastax.com/en/cql/3.3/cql/cql_using/useQueryDataTOC.html">Querying tables</a>.
   *
   * The configured read consistency level is used.
   *
   * You can return this `Source` as a response in a `ServiceCall`
   * and the elements will be streamed to the client.
   * Otherwise you have to connect a `Sink` that consumes the messages from
   * this `Source` and then `run` the stream.
   */
  @varargs
  def select(stmt: String, bindValues: AnyRef*): javadsl.Source[Row, NotUsed] =
    delegate.select(stmt, bindValues: _*)

  /**
   * Execute a select statement. First you must [[#prepare]] the statement and
   * bind its parameters. Only use this method when you know that the result
   * is small, e.g. includes a `LIMIT` clause. Otherwise you should use the
   * `select` method that returns a `Source`.
   *
   * The configured read consistency level is used if a specific consistency
   * level has not been set on the `Statement`.
   *
   * The returned `CompletionStage` is completed with the found rows.
   */
  def selectAll(stmt: Statement): CompletionStage[JList[Row]] =
    delegate.selectAll(stmt)

  /**
   * Prepare, bind and execute a select statement in one go. Only use this method
   * when you know that the result is small, e.g. includes a `LIMIT` clause.
   * Otherwise you should use the `select` method that returns a `Source`.
   *
   * The configured read consistency level is used.
   *
   * The returned `CompletionStage` is completed with the found rows.
   */
  @varargs
  def selectAll(stmt: String, bindValues: AnyRef*): CompletionStage[JList[Row]] =
    delegate.selectAll(stmt, bindValues: _*)

  /**
   * Execute a select statement that returns one row. First you must [[#prepare]] the
   * statement and bind its parameters.
   *
   * The configured read consistency level is used if a specific consistency
   * level has not been set on the `Statement`.
   *
   * The returned `CompletionStage` is completed with the first row,
   * if any.
   */
  def selectOne(stmt: Statement): CompletionStage[Optional[Row]] =
    delegate.selectOne(stmt)

  /**
   * Prepare, bind and execute a select statement that returns one row.
   *
   * The configured read consistency level is used.
   *
   * The returned `CompletionStage` is completed with the first row,
   * if any.
   */
  @varargs
  def selectOne(stmt: String, bindValues: AnyRef*): CompletionStage[Optional[Row]] =
    delegate.selectOne(stmt, bindValues: _*)

}
