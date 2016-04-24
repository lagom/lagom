/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.persistence.cassandra

import java.util.{ List => JList }
import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import java.util.function.{ Function => JFunction }

import scala.annotation.tailrec
import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Try

import akka.Done
import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.persistence.cassandra.CassandraPluginConfig
import akka.persistence.cassandra.SessionProvider
import akka.stream.ActorMaterializer
import akka.stream.Attributes
import akka.stream.Outlet
import akka.stream.SourceShape
import akka.stream.javadsl
import akka.stream.scaladsl
import akka.stream.stage.AsyncCallback
import akka.stream.stage.GraphStage
import akka.stream.stage.GraphStageLogic
import akka.stream.stage.OutHandler
import akka.util.Helpers.Requiring
import com.datastax.driver.core.BatchStatement
import com.datastax.driver.core.BoundStatement
import com.datastax.driver.core.ConsistencyLevel
import com.datastax.driver.core.PreparedStatement
import com.datastax.driver.core.ResultSet
import com.datastax.driver.core.Row
import com.datastax.driver.core.Session
import com.datastax.driver.core.Statement
import com.google.common.util.concurrent.ListenableFuture
import com.google.inject.Inject
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraSession
import com.typesafe.config.Config
import javax.inject.Singleton

private[lagom] object CassandraSessionImpl {
  implicit class ListenableFutureConverter[A](val lf: ListenableFuture[A]) extends AnyVal {
    def asScala(implicit ec: ExecutionContext): Future[A] = {
      val promise = Promise[A]
      lf.addListener(new Runnable { def run() = promise.complete(Try(lf.get())) }, ec.asInstanceOf[Executor])
      promise.future
    }
  }
}

@Singleton
private[lagom] class CassandraSessionImpl(system: ActorSystem, settings: CassandraSettings, executionContext: ExecutionContext)
  extends CassandraSession {
  import CassandraSessionImpl.ListenableFutureConverter
  import settings._

  @Inject
  def this(system: ActorSystem) =
    this(
      system,
      settings = new CassandraSettings(system.settings.config.getConfig(
      "lagom.persistence.read-side.cassandra"
    )),
      executionContext = system.dispatchers.lookup(system.settings.config.getString(
        "lagom.persistence.read-side.use-dispatcher"
      ))
    )

  private val sessionProvider: SessionProvider = {
    val className = settings.sessionProviderClassName
    val dynamicAccess = system.asInstanceOf[ExtendedActorSystem].dynamicAccess
    val clazz = dynamicAccess.getClassFor[SessionProvider](className).get
    def instantiate(args: immutable.Seq[(Class[_], AnyRef)]) =
      dynamicAccess.createInstanceFor[SessionProvider](clazz, args)

    val params = List((classOf[ActorSystem], system), (classOf[Config], settings.config))
    instantiate(params)
      .recoverWith { case x: NoSuchMethodException ⇒ instantiate(params.take(1)) }
      .recoverWith { case x: NoSuchMethodException ⇒ instantiate(Nil) }
      .recoverWith {
        case ex: Exception ⇒
          Failure(new IllegalArgumentException(s"Unable to create SessionProvider instance for class [$className], " +
            "tried constructor with ActorSystem, Config, and only ActorSystem, and no parameters", ex))
      }.get
  }

  implicit private val ec = executionContext
  private lazy implicit val materializer = ActorMaterializer()(system)

  // cache of PreparedStatement (PreparedStatement should only be prepared once)
  private val preparedStatements = new ConcurrentHashMap[String, Future[PreparedStatement]]
  private val computePreparedStatement = new JFunction[String, Future[PreparedStatement]] {
    override def apply(key: String): Future[PreparedStatement] =
      underlyingSession().flatMap { s =>
        val prepared = s.prepareAsync(key).asScala
        prepared.onFailure {
          case _ =>
            // this is async, i.e. we are not updating the map from the compute function
            preparedStatements.remove(key)
        }
        prepared
      }
  }

  private val _underlyingSession = new AtomicReference[Future[Session]]()

  @tailrec private def underlyingSession(): Future[Session] = {
    def close(s: Session): Unit = {
      s.closeAsync()
      s.getCluster().closeAsync()
    }

    def doWithSession(session: Future[Session])(query: Session => Future[_]): Future[Session] = {
      session.flatMap { s =>
        val result = query(s)
        result.onFailure { case _ => close(s) }
        result.map(_ => s)
      }
    }

    def init(session: Future[Session]): Future[Session] = {
      if (settings.keyspaceAutoCreate) {
        doWithSession(session) { s =>
          val result1 = s.executeAsync(s"""
                  CREATE KEYSPACE IF NOT EXISTS ${settings.keyspace}
                  WITH REPLICATION = { 'class' : ${settings.replicationStrategy} }
                  """).asScala
          result1.flatMap { _ =>
            s.executeAsync(s"USE ${settings.keyspace};").asScala
          }
        }
      } else if (settings.keyspace != "") {
        doWithSession(session) { s =>
          s.executeAsync(s"USE ${settings.keyspace};").asScala
        }
      } else {
        session
      }
    }

    val existing = _underlyingSession.get
    if (existing == null) {
      val s = init(sessionProvider.connect())
      if (_underlyingSession.compareAndSet(null, s)) {
        s.onFailure {
          case _ => _underlyingSession.compareAndSet(s, null)
        }
        system.registerOnTermination {
          s.foreach(close)
        }
        s
      } else {
        s.foreach(close)
        underlyingSession() // recursive
      }
    } else {
      existing
    }
  }

  override def underlying(): CompletionStage[Session] = {
    underlyingSession().toJava
  }

  override def executeCreateTable(stmt: String): CompletionStage[Done] = {
    val result: Future[Done] =
      for {
        s <- underlyingSession()
        _ <- s.executeAsync(stmt).asScala
      } yield Done

    result.toJava
  }

  override def prepare(stmt: String): CompletionStage[PreparedStatement] =
    underlyingSession().flatMap { _ =>
      preparedStatements.computeIfAbsent(stmt, computePreparedStatement)
    }.toJava

  override def executeWriteBatch(batch: BatchStatement): CompletionStage[Done] = {
    if (batch.getConsistencyLevel == null)
      batch.setConsistencyLevel(writeConsistency)
    val result: Future[Done] = underlyingSession().flatMap { s =>
      s.executeAsync(batch).asScala.map(_ => Done)
    }
    result.toJava
  }

  override def executeWrite(stmt: Statement): CompletionStage[Done] = {
    if (stmt.getConsistencyLevel == null)
      stmt.setConsistencyLevel(writeConsistency)
    val result: Future[Done] = underlyingSession().flatMap { s =>
      s.executeAsync(stmt).asScala.map(_ => Done)
    }
    result.toJava
  }

  @varargs
  override def executeWrite(stmt: String, bindValues: AnyRef*): CompletionStage[Done] = {
    val bound: Future[BoundStatement] = prepare(stmt).toScala.map { ps =>
      val bs = if (bindValues.isEmpty) ps.bind()
      else ps.bind(bindValues: _*)
      bs
    }
    bound.flatMap(b => executeWrite(b).toScala).toJava
  }

  override def select(stmt: Statement): javadsl.Source[Row, NotUsed] = {
    if (stmt.getConsistencyLevel == null)
      stmt.setConsistencyLevel(readConsistency)
    scaladsl.Source.fromGraph(new SelectSource(Future.successful(stmt))).asJava
  }

  @varargs
  override def select(stmt: String, bindValues: AnyRef*): javadsl.Source[Row, NotUsed] = {
    val bound: Future[BoundStatement] = prepare(stmt).toScala.map { ps =>
      val bs = if (bindValues.isEmpty) ps.bind()
      else ps.bind(bindValues: _*)
      bs.setConsistencyLevel(readConsistency)
      bs
    }
    scaladsl.Source.fromGraph(new SelectSource(bound)).asJava
  }

  override def selectAll(stmt: Statement): CompletionStage[JList[Row]] = {
    if (stmt.getConsistencyLevel == null)
      stmt.setConsistencyLevel(readConsistency)
    javadsl.Source.fromGraph(new SelectSource(Future.successful(stmt)))
      .runWith(javadsl.Sink.seq, materializer)
  }

  @varargs
  override def selectAll(stmt: String, bindValues: AnyRef*): CompletionStage[JList[Row]] = {
    val bound: Future[BoundStatement] = prepare(stmt).toScala.map(ps =>
      if (bindValues.isEmpty) ps.bind()
      else ps.bind(bindValues: _*))
    bound.flatMap(bs => selectAll(bs).toScala).toJava
  }

  override def selectOne(stmt: Statement): CompletionStage[Optional[Row]] = {
    if (stmt.getConsistencyLevel == null)
      stmt.setConsistencyLevel(readConsistency)
    javadsl.Source.fromGraph(new SelectSource(Future.successful(stmt)))
      .runWith(javadsl.Sink.headOption(), materializer)
  }

  @varargs
  override def selectOne(stmt: String, bindValues: AnyRef*): CompletionStage[Optional[Row]] = {
    val bound: Future[BoundStatement] = prepare(stmt).toScala.map(ps =>
      if (bindValues.isEmpty) ps.bind()
      else ps.bind(bindValues: _*))
    bound.flatMap(bs => selectOne(bs).toScala).toJava
  }

  private class SelectSource(stmt: Future[Statement]) extends GraphStage[SourceShape[Row]] {

    private val out: Outlet[Row] = Outlet("rows")
    override val shape: SourceShape[Row] = SourceShape(out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) {

        var asyncResult: AsyncCallback[ResultSet] = _
        var asyncFailure: AsyncCallback[Throwable] = _
        var resultSet: Option[ResultSet] = None

        override def preStart(): Unit = {
          asyncResult = getAsyncCallback[ResultSet] { rs =>
            resultSet = Some(rs)
            tryPushOne()
          }
          asyncFailure = getAsyncCallback { e =>
            fail(out, e)
          }
          stmt.onFailure { case e => asyncFailure.invoke(e) }
          stmt.foreach { s =>
            val rsFut = underlyingSession().flatMap(_.executeAsync(s).asScala)
            rsFut.onFailure { case e => asyncFailure.invoke(e) }
            rsFut.foreach(asyncResult.invoke)
          }
        }

        setHandler(out, new OutHandler {
          override def onPull(): Unit =
            tryPushOne()
        })

        def tryPushOne(): Unit =
          resultSet match {
            case Some(rs) if isAvailable(out) =>
              if (rs.isExhausted())
                complete(out)
              else if (rs.getAvailableWithoutFetching() > 0)
                push(out, rs.one())
              else {
                resultSet = None
                val rsFut = rs.fetchMoreResults().asScala
                rsFut.onFailure { case e => asyncFailure.invoke(e) }
                rsFut.foreach(asyncResult.invoke)
              }

            case _ =>
          }
      }
  }

}

private[lagom] class CassandraSettings(val config: Config) {
  import scala.collection.JavaConverters._

  val sessionProviderClassName = config.getString("session-provider")
  val fetchSize = config.getInt("max-result-size")
  val readConsistency: ConsistencyLevel = ConsistencyLevel.valueOf(config.getString("read-consistency"))
  val writeConsistency: ConsistencyLevel = ConsistencyLevel.valueOf(config.getString("write-consistency"))
  val keyspaceAutoCreate: Boolean = config.getBoolean("keyspace-autocreate")
  val keyspace: String = config.getString("keyspace").requiring(
    !keyspaceAutoCreate || _ > "",
    "'lagom.persistence.read-side.cassandra.keyspace' must be defined, or use keyspace-autocreate=off"
  )
  val replicationStrategy: String = CassandraPluginConfig.getReplicationStrategy(
    config.getString("replication-strategy"),
    config.getInt("replication-factor"),
    config.getStringList("data-center-replication-factors").asScala
  )
}
