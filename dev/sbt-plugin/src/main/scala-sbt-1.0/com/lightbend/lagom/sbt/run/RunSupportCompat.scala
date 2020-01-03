/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.sbt.run

import sbt._
import sbt.Keys._
import sbt.internal.Output
import xsbti.Position
import xsbti.Problem

import java.util.Optional

import play.api.PlayException
import play.sbt.PlayExceptions.CompilationException
import play.sbt.PlayExceptions.UnexpectedException
import com.lightbend.lagom.dev.Reloader.CompileFailure
import com.lightbend.lagom.dev.Reloader.CompileResult
import com.lightbend.lagom.dev.Reloader.CompileSuccess
import com.lightbend.lagom.dev.Reloader.Source

trait RunSupportCompat {

  def taskFailureHandler(incomplete: Incomplete, streams: Option[Streams]): PlayException = {
    Incomplete
      .allExceptions(incomplete)
      .headOption
      .map {
        case e: PlayException => e
        case e: xsbti.CompileFailed =>
          getProblems(incomplete, streams)
            .find(_.severity == xsbti.Severity.Error)
            .map(CompilationException)
            .getOrElse(UnexpectedException(Some("The compilation failed without reporting any problem!"), Some(e)))
        case e: Exception => UnexpectedException(unexpected = Some(e))
      }
      .getOrElse {
        UnexpectedException(Some("The compilation task failed without any exception!"))
      }
  }

  def originalSource(file: File): Option[File] = {
    play.twirl.compiler.MaybeGeneratedSource.unapply(file).map(_.file)
  }

  def compileFailure(streams: Option[Streams])(incomplete: Incomplete): CompileResult = {
    CompileFailure(taskFailureHandler(incomplete, streams))
  }

  def compile(
      reloadCompile: () => Result[sbt.internal.inc.Analysis],
      classpath: () => Result[Classpath],
      streams: () => Option[Streams]
  ): CompileResult = {
    reloadCompile().toEither.left
      .map(compileFailure(streams()))
      .right
      .map { analysis =>
        classpath().toEither.left
          .map(compileFailure(streams()))
          .right
          .map { classpath =>
            CompileSuccess(sourceMap(analysis), classpath.files)
          }
          .fold(identity, identity)
      }
      .fold(identity, identity)
  }

  def sourceMap(analysis: sbt.internal.inc.Analysis): Map[String, Source] = {
    analysis.relations.classes.reverseMap
      .mapValues { files =>
        val file = files.head // This is typically a set containing a single file, so we can use head here.
        Source(file, originalSource(file))
      }
  }

  def getScopedKey(incomplete: Incomplete): Option[ScopedKey[_]] = incomplete.node.flatMap {
    case key: ScopedKey[_] => Option(key)
    case task: Task[_]     => task.info.attributes.get(taskDefinitionKey)
  }

  def allProblems(inc: Incomplete): Seq[Problem] = {
    allProblems(inc :: Nil)
  }

  def allProblems(incs: Seq[Incomplete]): Seq[Problem] = {
    problems(Incomplete.allExceptions(incs).toSeq)
  }

  def problems(es: Seq[Throwable]): Seq[Problem] = {
    es.flatMap {
      case cf: xsbti.CompileFailed => cf.problems
      case _                       => Nil
    }
  }

  def getProblems(incomplete: Incomplete, streams: Option[Streams]): Seq[Problem] = {
    allProblems(incomplete) ++ {
      Incomplete.linearize(incomplete).flatMap(getScopedKey).flatMap { scopedKey =>
        val JavacError         = """\[error\]\s*(.*[.]java):(\d+):\s*(.*)""".r
        val JavacErrorInfo     = """\[error\]\s*([a-z ]+):(.*)""".r
        val JavacErrorPosition = """\[error\](\s*)\^\s*""".r

        streams
          .map { streamsManager =>
            var first: (Option[(String, String, String)], Option[Int])  = (None, None)
            var parsed: (Option[(String, String, String)], Option[Int]) = (None, None)
            Output
              .lastLines(scopedKey, streamsManager, None)
              .map(_.replace(scala.Console.RESET, ""))
              .map(_.replace(scala.Console.RED, ""))
              .collect {
                case JavacError(file, line, message) => parsed = Some((file, line, message)) -> None
                case JavacErrorInfo(key, message) =>
                  parsed._1.foreach { o =>
                    parsed = Some(
                      (
                        parsed._1.get._1,
                        parsed._1.get._2,
                        parsed._1.get._3 + " [" + key.trim + ": " + message.trim + "]"
                      )
                    ) -> None
                  }
                case JavacErrorPosition(pos) =>
                  parsed = parsed._1 -> Some(pos.length)
                  if (first == ((None, None))) {
                    first = parsed
                  }
              }
            first
          }
          .collect {
            case (Some(error), maybePosition) =>
              new Problem {
                def message: String = error._3
                def category        = ""
                def position: Position = new Position {
                  def line: Optional[Integer]   = Optional.ofNullable(error._2.toInt)
                  def lineContent: String       = ""
                  def offset: Optional[Integer] = Optional.empty[java.lang.Integer]
                  def pointer: Optional[Integer] =
                    maybePosition
                      .map(pos => Optional.ofNullable((pos - 1).asInstanceOf[java.lang.Integer]))
                      .getOrElse(Optional.empty[java.lang.Integer])
                  def pointerSpace: Optional[String] = Optional.empty[String]
                  def sourceFile: Optional[File]     = Optional.ofNullable(file(error._1))
                  def sourcePath: Optional[String]   = Optional.ofNullable(error._1)
                }
                def severity = xsbti.Severity.Error
              }
          }

      }
    }
  }

}
