/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.playjson

/**
 * Marker trait for classes that can be serialized using play-json part of the Scala API for Lagom
 *
 * It extends java.io.Serializable to give it higher priority than JavaSerializer
 * in Akka in case message class implements both interfaces.
 */
trait Jsonable extends Serializable
