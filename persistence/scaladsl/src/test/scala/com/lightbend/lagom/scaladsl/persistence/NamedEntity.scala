/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence

class NamedEntity() extends PersistentEntity {

  override type Command = String
  override type Event = String
  override type State = String

  override def entityTypeName: String = "some-name"

  override def initialState: State = ""

  override def behavior: Behavior = Actions()
}
