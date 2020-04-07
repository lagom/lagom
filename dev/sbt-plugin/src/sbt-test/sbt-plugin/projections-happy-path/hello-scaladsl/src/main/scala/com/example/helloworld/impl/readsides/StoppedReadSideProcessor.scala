/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.helloworld.impl.readsides

import java.util.concurrent.ConcurrentHashMap

import akka.Done
import akka.NotUsed
import akka.persistence.query.Offset
import akka.stream.scaladsl.Flow
import com.example.helloworld.impl.GreetingMessageChanged
import com.example.helloworld.impl.HelloWorldEvent
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag
import com.lightbend.lagom.scaladsl.persistence.EventStreamElement
import com.lightbend.lagom.scaladsl.persistence.ReadSide
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler

import scala.concurrent.Future

// -------- Started instance of the processor --------
object StartedReadSideProcessor {
  val Name = "StartedProcessor"
  private val greetings = new ConcurrentHashMap[String, String]()
}

class StartedReadSideProcessor(readSide: ReadSide)
    extends AbstractReadSideProcessor(
      readSide,
      StartedReadSideProcessor.Name,
      StartedReadSideProcessor.greetings
    )

// -------- Started instance of the processor --------
object StoppedReadSideProcessor {
  val Name = "StoppedProcessor"
  private val greetings = new ConcurrentHashMap[String, String]()
}

class StoppedReadSideProcessor(readSide: ReadSide)
    extends AbstractReadSideProcessor(
      readSide,
      StoppedReadSideProcessor.Name,
      StoppedReadSideProcessor.greetings
    )

// -------- Abstract  processor --------
class AbstractReadSideProcessor(private val readSide: ReadSide,
                                processorName: String,
                                inMemoryView: ConcurrentHashMap[String, String])
    extends ReadSideProcessor[HelloWorldEvent] {

  override def readSideName: String = processorName

  override def aggregateTags: Set[AggregateEventTag[HelloWorldEvent]] =
    HelloWorldEvent.Tag.allTags

  def getLastMessage(id: String): String =
    inMemoryView.getOrDefault(id, "default-projected-message")

  override def buildHandler()
    : ReadSideProcessor.ReadSideHandler[HelloWorldEvent] = {
    new ReadSideHandler[HelloWorldEvent] {

      val completedDone = Future.successful(Done)
      override def globalPrepare(): Future[Done] = completedDone

      override def prepare(
        tag: AggregateEventTag[HelloWorldEvent]
      ): Future[Offset] =
        Future.successful(Offset.noOffset)

      override def handle()
        : Flow[EventStreamElement[HelloWorldEvent], Done, NotUsed] = {
        Flow[EventStreamElement[HelloWorldEvent]]
          .mapAsync(1) { streamElement =>
            streamElement.event match {
              case GreetingMessageChanged(id, message) =>
                inMemoryView.put(id, message)
                completedDone
            }
          }
      }
    }
  }

}
