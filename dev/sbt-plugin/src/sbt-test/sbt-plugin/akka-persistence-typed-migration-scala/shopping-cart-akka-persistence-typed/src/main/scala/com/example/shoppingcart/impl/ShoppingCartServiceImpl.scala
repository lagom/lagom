/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
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
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry

import scala.concurrent.ExecutionContext
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import scala.concurrent.duration._
import akka.util.Timeout
import akka.cluster.sharding.typed.scaladsl.EntityRef

/**
 * Implementation of the `ShoppingCartService`.
 */
class ShoppingCartServiceImpl(
    clusterSharding: ClusterSharding,
    persistentEntityRegistry: PersistentEntityRegistry,
    reportRepository: ShoppingCartReportRepository
)(implicit ec: ExecutionContext)
    extends ShoppingCartService {

  /**
   * Looks up the shopping cart entity for the given ID.
   */
  private def entityRef(id: String): EntityRef[ShoppingCartCommand] =
    clusterSharding.entityRefFor(ShoppingCartState.typeKey, id)

  implicit val timeout = Timeout(5.seconds)

  override def get(id: String): ServiceCall[NotUsed, String] = ServiceCall { _ =>
    entityRef(id)
      .ask(reply => Get(reply))
      .map(cart => convertShoppingCart(id, cart))
  }

  override def updateItem(id: String, productId: String, qty: Int): ServiceCall[NotUsed, Done] = ServiceCall { update =>
    entityRef(id)
      .ask(reply => UpdateItem(productId, qty, reply))
      .map {
        case Accepted         => Done
        case Rejected(reason) => throw BadRequest(reason)
      }
  }

  override def checkout(id: String): ServiceCall[NotUsed, Done] = ServiceCall { _ =>
    entityRef(id)
      .ask(replyTo => Checkout(replyTo))
      .map {
        case Accepted         => Done
        case Rejected(reason) => throw BadRequest(reason)
      }
  }

  private def convertShoppingCart(id: String, cart: CurrentState): String = {
    val items = cart.state.items.map {case (k, v) => s"$k=$v"}.mkString(":")
    val status = if (cart.state.checkedOut) "checkedout" else "open"
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
