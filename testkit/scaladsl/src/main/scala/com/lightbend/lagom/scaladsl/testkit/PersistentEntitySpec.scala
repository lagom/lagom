/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.testkit

import akka.actor.ActorSystem
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.testkit.PersistentEntityTestDriver.Issue

object PersistentEntitySpec {

  /**
    * An exception to be thrown if the issues after running a test are non-empty.
    * @param entityId the entity which caused the issue.
    * @param issues the sequence of issues
    */
  final case class PersistentEntityHasIssuesException(entityId: String, issues: Seq[Issue]) extends Exception {
    override def getMessage: String = {
      s"Persistent entity $entityId had ${issues.size} issue(s)...\nFirst issue: ${issues.head}"
    }
  }
}

trait PersistentEntitySpec {

  type EntityCommand
  type EntityEvent
  type EntityState

  /**
    * The name of the actor system.
    * @return
    */
  def actorSystemName: String

  /**
    * The registry required to set up the actor system.
    * @return
    */
  def serializerRegistry: JsonSerializerRegistry

  protected def actorSystem: ActorSystem = ActorSystem(
    actorSystemName,
    JsonSerializerRegistry.actorSystemSetupFor(serializerRegistry)
  )

  /**
    * A fixture for injecting a [[PersistentEntityTestDriver]] into the a test
    *
    * @param entity the entity under test
    * @param entityId the id for the entity under test
    * @param block the test to be run
    * @return
    */
  protected def withDriver(entity: PersistentEntity {
    type Command = EntityCommand
    type Event = EntityEvent
    type State = EntityState
  }, entityId: String)(block: PersistentEntityTestDriver[EntityCommand, EntityEvent, EntityState] => Any): Any = {
    val driver = new PersistentEntityTestDriver(actorSystem, entity, entityId)
    try {
      block(driver)
    } finally {
      if (driver.getAllIssues.nonEmpty)
        throw PersistentEntitySpec.PersistentEntityHasIssuesException(entityId, driver.getAllIssues)
    }
  }
}
