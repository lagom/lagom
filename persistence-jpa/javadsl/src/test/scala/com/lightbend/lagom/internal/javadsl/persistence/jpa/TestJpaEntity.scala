/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.internal.javadsl.persistence.jpa

import javax.persistence.{ Entity, GeneratedValue, GenerationType, Id }

import scala.beans.BeanProperty

@Entity
class TestJpaEntity(@BeanProperty var data: String) {
  def this() = this(null)

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @BeanProperty
  var id: Integer = _ // use a boxed java.lang.Integer so it will be null by default
}
