/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.macrotestkit

import scala.language.experimental.macros
import java.util.regex.Pattern

import scala.reflect.macros.TypecheckException
import scala.reflect.macros.blackbox.Context

/**
 * A macro that ensures that a code snippet does not typecheck.
 */
object ShouldNotTypecheck {
  def apply(name: String, code: String): Unit = macro applyImplNoExp
  def apply(name: String, code: String, expected: String): Unit = macro applyImpl

  def applyImplNoExp(ctx: Context)(name: ctx.Expr[String], code: ctx.Expr[String]) = applyImpl(ctx)(name, code, null)

  def applyImpl(ctx: Context)(name: ctx.Expr[String], code: ctx.Expr[String], expected: ctx.Expr[String]): ctx.Expr[Unit] = {
    import ctx.universe._

    val Expr(Literal(Constant(codeStr: String))) = code
    val Expr(Literal(Constant(nameStr: String))) = name
    val (expPat, expMsg) = expected match {
      case null => (null, "Expected some error.")
      case Expr(Literal(Constant(s: String))) =>
        (Pattern.compile(s, Pattern.CASE_INSENSITIVE), "Expected error matching: " + s)
    }

    try ctx.typecheck(ctx.parse("{ " + codeStr + " }")) catch {
      case e: TypecheckException =>
        val msg = e.getMessage
        if ((expected ne null) && !expPat.matcher(msg).matches) {
          ctx.abort(ctx.enclosingPosition, s"$nameStr failed in an unexpected way.\n$expMsg\nActual error: $msg")
        } else {
          println(s"$nameStr passed.")
          return reify(())
        }
    }

    ctx.abort(ctx.enclosingPosition, s"$nameStr succeeded unexpectedly.\n$expMsg")
  }
}
