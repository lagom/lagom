/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl

import akka.japi.Effect

package object persistence {

  implicit class EffectOps(e: Effect) {
    def asScala: Function0[Unit] = () => {
      e.apply()
    }
  }

}
