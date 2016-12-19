/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.jpa

import javax.persistence.{ EntityManager, EntityTransaction }

import com.lightbend.lagom.javadsl.persistence.jdbc.JdbcPersistenceSpec
import com.lightbend.lagom.javadsl.persistence.jpa.JpaSession
import org.scalatest.matchers.{ BePropertyMatchResult, BePropertyMatcher }
import play.api.inject.DefaultApplicationLifecycle
import play.inject.DelegateApplicationLifecycle

import scala.compat.java8.FunctionConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, Future }

class JpaSessionImplSpec extends JdbcPersistenceSpec {
  protected lazy val applicationLifecycle = new DefaultApplicationLifecycle
  protected lazy val delegateApplicationLifecycle = new DelegateApplicationLifecycle(applicationLifecycle)
  protected lazy val jpa: JpaSession = new JpaSessionImpl(system, slick, delegateApplicationLifecycle)

  private val open = BePropertyMatcher[EntityManager] { entityManager =>
    BePropertyMatchResult(entityManager.isOpen, "open")
  }

  private val active = BePropertyMatcher[EntityTransaction] { entityTransaction =>
    BePropertyMatchResult(entityTransaction.isActive, "active")
  }

  override def afterAll(): Unit = {
    applicationLifecycle.stop()
    super.afterAll()
  }

  // Convenience for converting between Scala and Java 8
  private def withEntityManager[T](block: EntityManager => T): Future[T] =
    jpa.withEntityManager(block.asJava).toScala

  private def withTransaction[T](block: EntityManager => T): Future[T] =
    jpa.withTransaction(block.asJava).toScala

  "JpaSessionImpl" must {
    "provide an open EntityManager and close it when the block completes" in {
      val entityManager = Await.result(withEntityManager { entityManager =>
        entityManager shouldBe open
        entityManager
      }, 65.seconds)
      entityManager should not be null
      entityManager should not be open
    }

    "provide an active EntityTransaction and complete it when the block completes" in {
      val entityTransaction = Await.result(withTransaction { entityManager =>
        val transaction = entityManager.getTransaction
        transaction shouldBe active
        transaction
      }, 10.seconds)
      entityTransaction should not be null
      entityTransaction should not be active
    }

    "support saving and reading entities" in {
      val entity = new TestJpaEntity("test saving and reading entities")
      entity.getId shouldBe null

      Await.ready(withTransaction(_.persist(entity)), 10.seconds)

      val retrievedEntity = Await.result(withEntityManager {
        _.createQuery("SELECT test FROM TestJpaEntity test WHERE data = :data", classOf[TestJpaEntity])
          .setParameter("data", "test saving and reading entities")
          .getSingleResult
      }, 10.seconds)
      retrievedEntity.getId should not be null
      retrievedEntity.getData should equal("test saving and reading entities")
    }
  }
}
