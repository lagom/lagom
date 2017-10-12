/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import akka.actor.{ Actor, Props, Terminated }
import akka.util.Timeout
import com.lightbend.lagom.scaladsl.persistence.{ ChildPersistentEntityFactory, TestEntity, TestEntitySerializerRegistry }
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType

import scala.concurrent.duration._

class CassandraChildPersistentEntitySpec extends CassandraPersistenceSpec(TestEntitySerializerRegistry) {

  val add = TestEntity.Add("foo")
  val appended = TestEntity.Appended("FOO")
  val state = TestEntity.State(TestEntity.Mode.Append, List("FOO"))
  val get = TestEntity.Get

  "The child persistent entity factory" should {
    "allow tells" in {
      val actor = createActor("child-entity-tell")
      actor ! Tell(add)
      expectMsg(appended)
      actor ! Tell(get)
      expectMsg(state)
    }
    "allow asks" in {
      val actor = createActor("child-entity-ask")
      actor ! Ask(add)
      expectMsg(appended)
      actor ! Ask(get)
      expectMsg(state)
    }
    "allow forwards" in {
      val actor = createActor("child-entity-forward")
      actor ! Forward(add)
      expectMsg(appended)
      actor ! Forward(get)
      expectMsg(state)
    }
    "allow stopping entity" in {
      val actor = createActor("child-entity-stop")
      watch(actor)
      actor ! Stop
      expectTerminated(actor)
    }
  }

  private def createActor(entityId: String) = {
    val factory = ChildPersistentEntityFactory.forEntity(() => new TestEntity(system))
    system.actorOf(Props(new MyActor(factory, entityId)))
  }

  case class Ask(cmd: TestEntity.Cmd with ReplyType[_])
  case class Tell(cmd: TestEntity.Cmd)
  case object Stop
  case class Forward(cmd: TestEntity.Cmd)

  private class MyActor(factory: ChildPersistentEntityFactory[TestEntity], entityId: String) extends Actor {

    import akka.pattern.pipe
    import context.dispatcher

    implicit val timeout = Timeout(10.seconds)
    val entity = factory(entityId, "entity")
    context.watch(entity.actor)

    override def receive: Receive = {
      case Ask(cmd)      => (entity ? cmd) pipeTo sender()
      case Tell(cmd)     => entity.!(cmd)(sender())
      case Forward(cmd)  => entity forward cmd
      case Stop          => entity.stop()
      case Terminated(_) => context.stop(self)
    }
  }

}
