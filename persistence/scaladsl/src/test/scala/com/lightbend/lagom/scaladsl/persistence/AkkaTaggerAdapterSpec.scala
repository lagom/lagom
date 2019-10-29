/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.persistence

import akka.cluster.sharding.typed.scaladsl.EntityContext
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import org.scalatest.Matchers
import org.scalatest.WordSpec

/**
 *
 */
class AkkaTaggerAdapterSpec extends WordSpec with Matchers {
  val typeKey: EntityTypeKey[FakeCommand] = EntityTypeKey[FakeCommand]("fakeCommand")
  private val entityId                    = "123"

  val baseTagName = "baseName"
  val expectedTag = s"${baseTagName}0"

  "A AkkaTaggerAdapter" should {

    "produce Lagom-compatible tags" in {
      val entityContext: EntityContext[FakeCommand] = new EntityContext(typeKey, entityId, null)
      val tagger: FakeEvent => Set[String]          = AkkaTaggerAdapter.fromLagom(entityContext, FakeEvent.FakeEventTag)
      tagger(EventFaked) shouldBe Set(expectedTag)
    }

    "produce Lagom-compatible tags (without an EntityContext)" in {
      val tagger: FakeEvent => Set[String] = AkkaTaggerAdapter.fromLagom(entityId, FakeEvent.FakeEventTag)
      tagger(EventFaked) shouldBe Set(expectedTag)
    }

  }

  class FakeCommand

  object FakeEvent {
    val FakeEventTag: AggregateEventTagger[FakeEvent] = AggregateEventTag.sharded(baseTagName, 3)
  }

  sealed trait FakeEvent extends AggregateEvent[FakeEvent] {
    override def aggregateTag: AggregateEventTagger[FakeEvent] =
      FakeEvent.FakeEventTag
  }
  case object EventFaked extends FakeEvent

}
