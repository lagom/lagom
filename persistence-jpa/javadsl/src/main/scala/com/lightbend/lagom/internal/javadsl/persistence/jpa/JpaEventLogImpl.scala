/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.jpa

import java.util.UUID

import akka.actor.ActorSystem
import akka.serialization.{ SerializationExtension, SerializerWithStringManifest }
import javax.inject.Inject
import javax.persistence.EntityManager
import com.lightbend.lagom.internal.javadsl.persistence.jdbc.SlickProvider
import com.lightbend.lagom.javadsl.persistence.{ AggregateEvent, AggregateEventShards, AggregateEventTag }
import com.lightbend.lagom.javadsl.persistence.jdbc.EventLogEntityType
import com.lightbend.lagom.javadsl.persistence.jpa.JpaEventLog
import play.api.inject.Injector

class JpaEventLogImpl @Inject() (slickProvider: SlickProvider, injector: Injector, system: ActorSystem) extends JpaEventLog {

  private val serialization = SerializationExtension(system)

  override def eventLogFor[Event <: AggregateEvent[Event]](entityType: Class[_ <: EventLogEntityType[Event]]): JpaEventLog.EntityEventLog[Event] = {
    val entityTypeName: String = injector.instanceOf(entityType).entityTypeName
    new EntityEventLogImpl[Event](entityTypeName)
  }

  import slickProvider.profile.api._

  private class EntityEventLogImpl[Event <: AggregateEvent[Event]](entityTypeName: String) extends JpaEventLog.EntityEventLog[Event] {
    override def emit(em: EntityManager, entityId: String, event: Event): Unit = {
      val serializer = serialization.findSerializerFor(event)
      val bytes = serializer.toBinary(event)
      val manifest = serializer match {
        case stringManifest: SerializerWithStringManifest =>
          stringManifest.manifest(event)
        case _ if serializer.includeManifest =>
          event.getClass.getName
        case _ => ""
      }
      val tag = event.aggregateTag match {
        case single: AggregateEventTag[_]     => single.tag
        case sharded: AggregateEventShards[_] => sharded.forEntityId(entityId).tag
      }
      val statements = insertEvent(entityTypeName + entityId, tag, bytes, serializer.identifier, manifest).statements
      statements.foreach { statement =>
        // Most parameters will have already been directly substituted by Slick, the only one that hasn't is the byte
        // array
        em.createNativeQuery(statement)
          .setParameter(1, bytes)
          .executeUpdate()
      }
    }
  }

  private def maxSequenceNumber(fullEntityId: String): Rep[Long] = {
    slickProvider.journalTables.JournalTable
      .filter(_.persistenceId === fullEntityId)
      .map(_.sequenceNumber)
      .max
      .ifNull(0L)
  }

  private def nextSequenceNumber(fullEntityId: String): Rep[Long] = {
    // If two insert operations happen at the same time, they'll get the same
    // next sequence number. That's ok, because the sequence number is part of
    // the primary key, so one of them will fail. I don't think there is any
    // other way to handle it other than failing though, this operation is
    // likely to be combined with some other CRUD operation on the schema, if
    // we're unable to insert the event, we need that CRUD operation to fail
    // otherwise we can't guarantee the atomicity of the CRUD operation and the
    // event insert.
    maxSequenceNumber(fullEntityId) + 1L
  }

  private def insertEvent(fullEntityId: String, tag: String, serializedEvent: Array[Byte], serId: Int, manifest: String) = {
    // This compiles to a single statement, along the lines of:
    // insert into journal (persistenceId, sequenceNumber, message)
    //   select <id>, coalesce(max(sequenceNumber), 0), <bytes>
    //   from journal where persistenceId = <id>
    slickProvider.journalTables.JournalTable
      .map(t => (t.persistenceId, t.sequenceNumber, t.deleted, t.tags, t.event, t.eventManifest, t.serId, t.serManifest, t.writerUuid))
      .forceInsertExpr((fullEntityId, nextSequenceNumber(fullEntityId), false, Some(tag), Some(serializedEvent), Some(""), Some(serId),
        Some(manifest), Some(UUID.randomUUID().toString)))
  }
}
