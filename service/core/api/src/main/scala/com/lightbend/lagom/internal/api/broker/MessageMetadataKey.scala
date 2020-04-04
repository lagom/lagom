/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.internal.api.broker

/**
 * A metadata key.
 * @param name The name of the metadata key.
 * @tparam Type Type of metadata value.
 */
final case class MessageMetadataKey[Type](name: String)

object MessageMetadataKey {

  /**
   * The message key metadata key.
   */
  def messageKey[Type]: MessageMetadataKey[Type] = MessageKeyObj.asInstanceOf[MessageMetadataKey[Type]]

  def named[Type](name: String): MessageMetadataKey[Type] = MessageMetadataKey(name);

  private val MessageKeyObj = MessageMetadataKey[Any]("messageKey")

}
