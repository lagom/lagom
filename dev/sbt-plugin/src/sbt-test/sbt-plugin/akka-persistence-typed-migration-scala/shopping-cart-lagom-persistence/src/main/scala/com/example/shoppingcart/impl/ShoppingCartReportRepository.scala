/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.impl

import java.time.Instant

import akka.Done
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * The report repository manage the storage of ShoppingCartReport which is a API class (view model).
 *
 * It saves data in a ready for consumption format for that specific API model.
 * If the API changes, we must regenerated the stored models.
 */
class ShoppingCartReportRepository(database: Database) {

  class ShoppingCartReportTable(tag: Tag) extends Table[ShoppingCartReport](tag, "shopping_cart_report") {
    def cartId = column[String]("cart_id", O.PrimaryKey)

    def created = column[Boolean]("created")

    def checkedOut = column[Boolean]("checked_out")

    def * = (cartId, created, checkedOut) <> ((ShoppingCartReport.apply _).tupled, ShoppingCartReport.unapply)
  }

  val reportTable = TableQuery[ShoppingCartReportTable]

  def createTable() = reportTable.schema.createIfNotExists

  def findById(id: String): Future[Option[ShoppingCartReport]] =
    database.run(findByIdQuery(id))

  def createReport(cartId: String): DBIO[Done] = {
    findByIdQuery(cartId).flatMap {
      case None => reportTable += ShoppingCartReport(cartId, created = true,  checkedOut = false)
      case _ => DBIO.successful(Done)
    }.map(_ => Done).transactionally
  }

  def addCheckoutTime(cartId: String): DBIO[Done] = {
    findByIdQuery(cartId).flatMap {
      case Some(cart) => reportTable.insertOrUpdate(cart.copy(checkedOut = true))
      // if that happens we have a corrupted system
      // cart checkout can only happens for a existing cart
      case None => throw new RuntimeException(s"Didn't find cart for checkout. CartID: $cartId")
    }.map(_ => Done).transactionally
  }

  private def findByIdQuery(cartId: String): DBIO[Option[ShoppingCartReport]] =
    reportTable
      .filter(_.cartId === cartId)
      .result.headOption
}


case class ShoppingCartReport(cartId: String, created: Boolean, checkedOut: Boolean)
