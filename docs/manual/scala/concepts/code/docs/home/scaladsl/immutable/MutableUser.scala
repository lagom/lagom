/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.scaladsl.immutable

//#mutable
class MutableUser {
  private var name: String  = null
  private var email: String = null

  def getName: String = name

  def setName(name: String): Unit =
    this.name = name

  def getEmail: String = email

  def setEmail(email: String): Unit =
    this.email = email
}
//#mutable
