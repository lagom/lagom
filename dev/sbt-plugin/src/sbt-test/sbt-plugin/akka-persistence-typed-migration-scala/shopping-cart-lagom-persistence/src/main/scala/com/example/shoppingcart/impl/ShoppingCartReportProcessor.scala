/*
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.shoppingcart.impl

import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor
import com.lightbend.lagom.scaladsl.persistence.slick.SlickReadSide


class ShoppingCartReportProcessor(readSide: SlickReadSide,
                                  repository: ShoppingCartReportRepository) extends ReadSideProcessor[ShoppingCartEvent] {

  override def buildHandler() =
    readSide
      .builder[ShoppingCartEvent]("shopping-cart-report")
      .setGlobalPrepare(repository.createTable())
      .setEventHandler[ItemUpdated] { envelope =>
        repository.createReport(envelope.entityId)
      }
      .setEventHandler[CheckedOut.type] { envelope =>
        repository.addCheckoutTime(envelope.entityId)
      }
      .build()

  override def aggregateTags = ShoppingCartEvent.Tag.allTags
}
