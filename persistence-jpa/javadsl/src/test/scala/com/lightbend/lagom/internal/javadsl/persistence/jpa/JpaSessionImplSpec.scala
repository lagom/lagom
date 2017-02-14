/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.jpa

import javax.persistence.{ EntityManager, EntityTransaction, Persistence }

import com.google.common.collect.ImmutableMap
import com.lightbend.lagom.javadsl.persistence.jpa.TestJpaEntity
import org.scalatest.matchers.{ BePropertyMatchResult, BePropertyMatcher }

import scala.compat.java8.FunctionConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ Await, Future }

class JpaSessionImplSpec extends JpaPersistenceSpec {
  private val open = BePropertyMatcher[EntityManager] { entityManager =>
    BePropertyMatchResult(entityManager.isOpen, "open")
  }

  private val active = BePropertyMatcher[EntityTransaction] { entityTransaction =>
    BePropertyMatchResult(entityTransaction.isActive, "active")
  }

  // Convenience for converting between Scala and Java 8
  private def withTransaction[T](block: EntityManager => T): Future[T] =
    jpa.withTransaction(block.asJava).toScala

  "JpaSessionImpl" must {
    "provide an open EntityManager and close it when the block completes" in {
      val entityManager = Await.result(withTransaction { entityManager =>
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
      Persistence.generateSchema("default", ImmutableMap.of("hibernate.hbm2ddl.auto", "update"))
      val entity = new TestJpaEntity("1", "test saving and reading entities")
      entity.getId shouldBe null

      Await.ready(withTransaction(_.persist(entity)), 10.seconds)

      // Note that the retrieval runs in a new transaction
      val retrievedEntity = Await.result(withTransaction {
        _.createQuery("SELECT test FROM TestJpaEntity test WHERE parentId = :parentId", classOf[TestJpaEntity])
          .setParameter("parentId", "1")
          .getSingleResult
      }, 10.seconds)
      retrievedEntity.getId should not be null
      retrievedEntity.getParentId should equal("1")
      retrievedEntity.getElement should equal("test saving and reading entities")
    }
  }
}
