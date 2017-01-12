/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.maven

import scala.beans.BeanProperty

class PortRangeBean {
  @BeanProperty var min: Int = 0xc000 // 49152, IANA minimum ephemeral port
  @BeanProperty var max: Int = 0xffff // 65535, IANA maximum ephemeral port
}
