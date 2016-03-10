/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra

import java.util.Optional
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.stream.ActorMaterializer
import akka.stream.testkit.scaladsl.TestSink
import com.datastax.driver.core.BatchStatement
import com.datastax.driver.core.SimpleStatement
import com.typesafe.config.ConfigFactory
import com.lightbend.lagom.internal.persistence.cassandra.CassandraSessionImpl
import com.lightbend.lagom.javadsl.persistence.PersistenceSpec

object CassandraSessionSpec {

  val config = ConfigFactory.parseString(s"""
    akka.loglevel = INFO
    lagom.persistence.read-side.cassandra.max-result-size = 2
    """)

}

class CassandraSessionSpec extends PersistenceSpec(CassandraSessionSpec.config) {
  import CassandraSessionSpec._
  import system.dispatcher

  implicit val materializer = ActorMaterializer()(system)

  lazy val session: CassandraSession = new CassandraSessionImpl(system)

  override def beforeAll {
    super.beforeAll()
    createTable()
    insertTestData()
  }

  def createTable(): Unit = {
    Await.ready(session.executeCreateTable(s"""
      CREATE TABLE IF NOT EXISTS testcounts (
        partition text,
        key text,
        count bigint,
        PRIMARY KEY (partition, key))
        """).toScala, 15.seconds)
  }

  def insertTestData(): Unit = {
    val batch = new BatchStatement
    batch.add(new SimpleStatement("INSERT INTO testcounts (partition, key, count) VALUES ('A', 'a', 1);"))
    batch.add(new SimpleStatement("INSERT INTO testcounts (partition, key, count) VALUES ('A', 'b', 2);"))
    batch.add(new SimpleStatement("INSERT INTO testcounts (partition, key, count) VALUES ('A', 'c', 3);"))
    batch.add(new SimpleStatement("INSERT INTO testcounts (partition, key, count) VALUES ('A', 'd', 4);"))
    batch.add(new SimpleStatement("INSERT INTO testcounts (partition, key, count) VALUES ('B', 'e', 5);"))
    batch.add(new SimpleStatement("INSERT INTO testcounts (partition, key, count) VALUES ('B', 'f', 6);"))
    Await.ready(session.executeWriteBatch(batch).toScala, 10.seconds)
  }

  "CassandraSession" must {

    "select prepared statement as Source" in {
      val stmt = Await.result(session.prepare(
        "SELECT count FROM testcounts WHERE partition = ?"
      ).toScala, 5.seconds)
      val bound = stmt.bind("A")
      val rows = session.select(bound).asScala
      val probe = rows.map(_.getLong("count")).runWith(TestSink.probe[Long])
      probe.within(10.seconds) {
        probe.request(10)
          .expectNextUnordered(1L, 2L, 3L, 4L)
          .expectComplete()
      }
    }

    "select and bind as Source" in {
      val rows = session.select("SELECT count FROM testcounts WHERE partition = ?", "B").asScala
      val probe = rows.map(_.getLong("count")).runWith(TestSink.probe[Long])
      probe.within(10.seconds) {
        probe.request(10)
          .expectNextUnordered(5L, 6L)
          .expectComplete()
      }
    }

    "selectAll and bind" in {
      val rows = Await.result(session.selectAll(
        "SELECT count FROM testcounts WHERE partition = ?", "A"
      ).toScala, 5.seconds)
      rows.asScala.map(_.getLong("count")).toSet should ===(Set(1L, 2L, 3L, 4L))
    }

    "selectAll empty" in {
      val rows = Await.result(session.selectAll(
        "SELECT count FROM testcounts WHERE partition = ?", "X"
      ).toScala, 5.seconds)
      rows.isEmpty should ===(true)
    }

    "selectOne and bind" in {
      val row = Await.result(session.selectOne(
        "SELECT count FROM testcounts WHERE partition = ? and key = ?", "A", "b"
      ).toScala, 5.seconds)
      row.get.getLong("count") should ===(2L)
    }

    "selectOne empty" in {
      val row = Await.result(session.selectOne(
        "SELECT count FROM testcounts WHERE partition = ? and key = ?", "A", "x"
      ).toScala, 5.seconds)
      row should be(Optional.empty())
    }

  }

}

