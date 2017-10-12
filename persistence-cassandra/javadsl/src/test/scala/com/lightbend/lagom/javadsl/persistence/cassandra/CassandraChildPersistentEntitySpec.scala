/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.cassandra

import akka.actor.{ Actor, Props, Terminated }
import akka.util.Timeout
import com.google.common.collect.ImmutableList
import com.lightbend.lagom.javadsl.persistence.PersistentEntity.ReplyType
import com.lightbend.lagom.javadsl.persistence.{ ChildPersistentEntityFactory, TestEntity }
import play.api.inject.{ NewInstanceInjector, SimpleInjector }

import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters

class CassandraChildPersistentEntitySpec extends CassandraPersistenceSpec() {

  val add = new TestEntity.Add("foo", 1)
  def appended(entityId: String) = new TestEntity.Appended(entityId, "FOO")
  val state = new TestEntity.State(TestEntity.Mode.APPEND, ImmutableList.copyOf(List("FOO").toIterable.asJava))
  val get = TestEntity.Get.instance()

  "The child persistent entity factory" should {
    "allow tells" in {
      val actor = createActor("child-entity-tell")
      actor ! Tell(add)
      expectMsg(appended("child-entity-tell"))
      actor ! Tell(get)
      expectMsg(state)
    }
    "allow asks" in {
      val actor = createActor("child-entity-ask")
      actor ! Ask(add)
      expectMsg(appended("child-entity-ask"))
      actor ! Ask(get)
      expectMsg(state)
    }
    "allow forwards" in {
      val actor = createActor("child-entity-forward")
      actor ! Forward(add)
      expectMsg(appended("child-entity-forward"))
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
    val factory = ChildPersistentEntityFactory.forEntity(
      classOf[TestEntity],
      (new SimpleInjector(NewInstanceInjector) + new TestEntity(system)).asJava
    )
    system.actorOf(Props(new MyActor(factory, entityId)))
  }

  case class Ask(cmd: TestEntity.Cmd with ReplyType[_])
  case class Tell(cmd: TestEntity.Cmd)
  case object Stop
  case class Forward(cmd: TestEntity.Cmd)

  private class MyActor(factory: ChildPersistentEntityFactory[TestEntity.Cmd], entityId: String) extends Actor {

    import akka.pattern.pipe
    import context.dispatcher

    private val timeout = Timeout(10.seconds)
    private val entity = factory.create(entityId, "entity", context)
    context.watch(entity.getActor)

    override def receive: Receive = {
      case Ask(cmd: TestEntity.Cmd with ReplyType[Unit]) =>
        FutureConverters.toScala(entity.ask[Unit, TestEntity.Cmd with ReplyType[Unit]](cmd, timeout)) pipeTo sender()
      case Tell(cmd)     => entity.tell(cmd, sender())
      case Forward(cmd)  => entity.forward(cmd, context)
      case Stop          => entity.stop()
      case Terminated(_) => context.stop(self)
    }
  }

}
