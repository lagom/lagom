/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.impl

import java.time.OffsetDateTime

import akka.Done
import akka.NotUsed
import com.example.shoppingcart.api.ShoppingCartService
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.transport.BadRequest
import com.lightbend.lagom.scaladsl.api.transport.NotFound
import com.lightbend.lagom.scaladsl.api.transport.TransportException
import com.lightbend.lagom.scaladsl.persistence.EventStreamElement

import scala.concurrent.ExecutionContext
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import scala.concurrent.duration._
import akka.util.Timeout
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.actor.typed.ActorRef

/**
 * Implementation of the `ShoppingCartService`.
 */
class ShoppingCartServiceImpl(
    clusterSharding: ClusterSharding,
    reportRepository: ShoppingCartReportRepository
)(implicit ec: ExecutionContext)
    extends ShoppingCartService {

  //#akka-persistence-reffor-after
  /**
   * Looks up the shopping cart entity for the given ID.
   */
  private def entityRef(id: String): EntityRef[ShoppingCartCommand] =
    clusterSharding.entityRefFor(ShoppingCart.typeKey, id)

  implicit val timeout = Timeout(5.seconds)

  override def get(id: String): ServiceCall[NotUsed, String] = ServiceCall { _ =>
    entityRef(id)
      .ask { reply: ActorRef[Summary] => Get(reply) }
      .map { cart => asShoppingCartView(id, cart) }
  }
  //#akka-persistence-reffor-after

  override def updateItem(id: String, productId: String, qty: Int): ServiceCall[NotUsed, String] = ServiceCall { update =>
    entityRef(id)
      .ask { replyTo: ActorRef[Confirmation] => UpdateItem(productId, qty, replyTo) }
      .map {
        case Accepted(summary)  => asShoppingCartView(id, summary)
        case Rejected(reason)   => throw BadRequest(reason)
      }
  }

  override def checkout(id: String): ServiceCall[NotUsed, String] = ServiceCall { _ =>
    entityRef(id)
      .ask(replyTo => Checkout(replyTo))
      .map {
        case Accepted(summary)  => asShoppingCartView(id, summary)
        case Rejected(reason)   => throw BadRequest(reason)
      }
  }

  private def asShoppingCartView(id: String, cart: Summary): String = {
    val items = cart.items.map {case (k, v) => s"$k=$v"}.mkString(":")
    val status = if (cart.checkedOut) "checkedout" else "open"
    s"$id:$items:$status"
  }

  override def getReport(cartId: String): ServiceCall[NotUsed, String] = ServiceCall { _ =>
    reportRepository.findById(cartId).map {
      case Some(cart) =>
      if (cart.checkedOut) "checkedout"
      else "open"
      case None => throw NotFound(s"Couldn't find a shopping cart report for '$cartId'")
    }
  }


}
