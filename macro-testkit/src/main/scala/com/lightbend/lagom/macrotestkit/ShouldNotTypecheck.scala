/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.macrotestkit

import scala.language.experimental.macros
import java.util.regex.Pattern

import scala.reflect.macros.TypecheckException
import scala.reflect.macros.blackbox

/**
 * A macro that ensures that a code snippet does not typecheck.
 */
object ShouldNotTypecheck {
  def apply(name: String, code: String): Unit = macro ShouldNotTypecheck.applyImplNoExp
  def apply(name: String, code: String, expected: String): Unit = macro ShouldNotTypecheck.applyImpl
}

final class ShouldNotTypecheck(val c: blackbox.Context) {
  import c.universe._

  def applyImplNoExp(name: Expr[String], code: Expr[String]): Expr[Unit] = applyImpl(name, code, c.Expr(EmptyTree))

  def applyImpl(name: Expr[String], code: Expr[String], expected: Expr[String]): Expr[Unit] = {
    val Expr(Literal(Constant(codeStr: String))) = code
    val Expr(Literal(Constant(nameStr: String))) = name
    val (expPat, expMsg) = expected.tree match {
      case EmptyTree => (Pattern.compile(".*"), "Expected some error.")
      case Literal(Constant(s: String)) =>
        (Pattern.compile(s, Pattern.CASE_INSENSITIVE), "Expected error matching: " + s)
    }

    try c.typecheck(c.parse("{ " + codeStr + " }")) catch {
      case e: TypecheckException =>
        val msg = e.getMessage
        if (!expPat.matcher(msg).matches) {
          c.abort(c.enclosingPosition, s"$nameStr failed in an unexpected way.\n$expMsg\nActual error: $msg")
        } else {
          println(s"$nameStr passed.")
          return reify(())
        }
    }

    c.abort(c.enclosingPosition, s"$nameStr succeeded unexpectedly.\n$expMsg")
  }
}
