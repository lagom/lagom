/*
 * Copyright (C) 2016-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.it.routers

import com.lightbend.lagom.scaladsl.api.Service.named
import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service }

trait AdditionalRoutersService extends Service {

  override def descriptor: Descriptor = named("additional-routers")

}
