/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.sbt.run

import com.lightbend.lagom.dev.Reloader
import com.lightbend.lagom.dev.Reloader.{ CompileFailure, CompileResult, CompileSuccess, Source }
import com.lightbend.lagom.sbt.Internal
import com.lightbend.lagom.sbt.LagomPlugin.autoImport._
import com.lightbend.lagom.sbt.LagomReloadableService.autoImport._
import sbt._
import sbt.Keys._
import play.api.PlayException
import play.sbt.PlayExceptions._

private[sbt] object RunSupport {

  def reloadRunTask(
    extraConfigs: Map[String, String]
  ): Def.Initialize[Task[Reloader.DevServer]] = Def.task {

    val state = Keys.state.value
    val scope = resolvedScoped.value.scope

    val reloadCompile = () => RunSupport.compile(
      () => Project.runTask(lagomReload in scope, state).map(_._2).get,
      () => Project.runTask(lagomReloaderClasspath in scope, state).map(_._2).get,
      () => Project.runTask(streamsManager in scope, state).map(_._2).get.toEither.right.toOption
    )

    val classpath = (devModeDependencies.value ++ (externalDependencyClasspath in Runtime).value).distinct.files

    Reloader.startDevMode(
      scalaInstance.value.loader,
      classpath,
      reloadCompile,
      lagomWatchDirectories.value,
      lagomFileWatchService.value,
      baseDirectory.value,
      extraConfigs.toSeq ++ lagomDevSettings.value,
      lagomServicePort.value,
      RunSupport
    )
  }

  def nonReloadRunTask(
    extraConfigs: Map[String, String]
  ): Def.Initialize[Task[Reloader.DevServer]] = Def.task {

    val classpath = (devModeDependencies.value ++ (fullClasspath in Runtime).value).distinct

    val buildLinkSettings = extraConfigs.toSeq ++ lagomDevSettings.value

    Reloader.startNoReload(scalaInstance.value.loader, classpath.map(_.data), baseDirectory.value, buildLinkSettings,
      lagomServicePort.value)
  }

  private def devModeDependencies = Def.task {
    (managedClasspath in Internal.Configs.DevRuntime).value
  }

  def compile(reloadCompile: () => Result[sbt.inc.Analysis], classpath: () => Result[Classpath], streams: () => Option[Streams]): CompileResult = {
    reloadCompile().toEither
      .left.map(compileFailure(streams()))
      .right.map { analysis =>
        classpath().toEither
          .left.map(compileFailure(streams()))
          .right.map { classpath =>
            CompileSuccess(sourceMap(analysis), classpath.files)
          }.fold(identity, identity)
      }.fold(identity, identity)
  }

  def sourceMap(analysis: sbt.inc.Analysis): Map[String, Source] = {
    analysis.apis.internal.foldLeft(Map.empty[String, Source]) {
      case (sourceMap, (file, source)) => sourceMap ++ {
        source.api.definitions map { d => d.name -> Source(file, originalSource(file)) }
      }
    }
  }

  def originalSource(file: File): Option[File] = {
    play.twirl.compiler.MaybeGeneratedSource.unapply(file).map(_.file)
  }

  def compileFailure(streams: Option[Streams])(incomplete: Incomplete): CompileResult = {
    CompileFailure(taskFailureHandler(incomplete, streams))
  }

  def taskFailureHandler(incomplete: Incomplete, streams: Option[Streams]): PlayException = {
    Incomplete.allExceptions(incomplete).headOption.map {
      case e: PlayException => e
      case e: xsbti.CompileFailed =>
        getProblems(incomplete, streams)
          .find(_.severity == xsbti.Severity.Error)
          .map(CompilationException)
          .getOrElse(UnexpectedException(Some("The compilation failed without reporting any problem!"), Some(e)))
      case e: Exception => UnexpectedException(unexpected = Some(e))
    }.getOrElse {
      UnexpectedException(Some("The compilation task failed without any exception!"))
    }
  }

  def getScopedKey(incomplete: Incomplete): Option[ScopedKey[_]] = incomplete.node flatMap {
    case key: ScopedKey[_] => Option(key)
    case task: Task[_]     => task.info.attributes get taskDefinitionKey
  }

  def getProblems(incomplete: Incomplete, streams: Option[Streams]): Seq[xsbti.Problem] = {
    allProblems(incomplete) ++ {
      Incomplete.linearize(incomplete).flatMap(getScopedKey).flatMap { scopedKey =>
        val JavacError = """\[error\]\s*(.*[.]java):(\d+):\s*(.*)""".r
        val JavacErrorInfo = """\[error\]\s*([a-z ]+):(.*)""".r
        val JavacErrorPosition = """\[error\](\s*)\^\s*""".r

        streams.map { streamsManager =>
          var first: (Option[(String, String, String)], Option[Int]) = (None, None)
          var parsed: (Option[(String, String, String)], Option[Int]) = (None, None)
          Output.lastLines(scopedKey, streamsManager, None).map(_.replace(scala.Console.RESET, "")).map(_.replace(scala.Console.RED, "")).collect {
            case JavacError(file, line, message) => parsed = Some((file, line, message)) -> None
            case JavacErrorInfo(key, message) => parsed._1.foreach { o =>
              parsed = Some((parsed._1.get._1, parsed._1.get._2, parsed._1.get._3 + " [" + key.trim + ": " + message.trim + "]")) -> None
            }
            case JavacErrorPosition(pos) =>
              parsed = parsed._1 -> Some(pos.size)
              if (first == ((None, None))) {
                first = parsed
              }
          }
          first
        }.collect {
          case (Some(error), maybePosition) => new xsbti.Problem {
            def message = error._3
            def category = ""
            def position = new xsbti.Position {
              def line = xsbti.Maybe.just(error._2.toInt)
              def lineContent = ""
              def offset = xsbti.Maybe.nothing[java.lang.Integer]
              def pointer = maybePosition.map(pos => xsbti.Maybe.just((pos - 1).asInstanceOf[java.lang.Integer])).getOrElse(xsbti.Maybe.nothing[java.lang.Integer])
              def pointerSpace = xsbti.Maybe.nothing[String]
              def sourceFile = xsbti.Maybe.just(file(error._1))
              def sourcePath = xsbti.Maybe.just(error._1)
            }
            def severity = xsbti.Severity.Error
          }
        }

      }
    }
  }

  def allProblems(inc: Incomplete): Seq[xsbti.Problem] = {
    allProblems(inc :: Nil)
  }

  def allProblems(incs: Seq[Incomplete]): Seq[xsbti.Problem] = {
    problems(Incomplete.allExceptions(incs).toSeq)
  }

  def problems(es: Seq[Throwable]): Seq[xsbti.Problem] = {
    es flatMap {
      case cf: xsbti.CompileFailed => cf.problems
      case _                       => Nil
    }
  }

}
