package lagom.build

import java.nio.file.{ Files, Paths }

import scala.collection.JavaConverters._

import sbt._
import sbt.Keys._

object VersionSyncCheckPlugin extends AutoPlugin {
  override def trigger = allRequirements

  object autoImport {
    val versionSyncCheck = taskKey[Unit]("")
  }
  import autoImport._

  override def globalSettings = Seq(
    versionSyncCheck := versionSyncCheckImpl.value
  )

  final case class Entry(name: String, expectedVersion: String, valName: String)

  val entries = Seq(
    Entry("Scala", Dependencies.Versions.Scala212, "ScalaVersion"),
    Entry("Play", Dependencies.Versions.Play, "PlayVersion"),
    Entry("Akka", Dependencies.Versions.Akka, "AkkaVersion"),
    Entry("ScalaTest", Dependencies.Versions.ScalaTest, "ScalaTestVersion"),
    Entry("JUnit", Dependencies.Versions.JUnit, "JUnitVersion"),
    Entry("JUnitInterface", Dependencies.Versions.JUnitInterface, "JUnitInterfaceVersion"),
    Entry("Log4j", Dependencies.Versions.Log4j, "Log4jVersion")
  )

  def versionSyncCheckImpl = Def.task[Unit] {
    val log = state.value.log

    log.info("Running version sync check")

    val docsBuildLines = Files.lines(Paths.get("docs/build.sbt")).iterator.asScala.toStream

    val result = for (entry <- entries) yield {
      val Entry(name, expectedVersion, valName) = entry
      val Regex = raw"""val $valName[: ].*"(\d+\.\d+(?:\.\d+)?)"""".r.unanchored

      val unexpectedVersions = (for (Regex(version) <- docsBuildLines) yield version) match {
        case Stream(version) => if (version == expectedVersion) "" else version
        case Stream()        => "<none>"
        case multiple        => multiple.mkString("multiple: ", ", ", "")
      }

      if (unexpectedVersions == "") {
        log.info(s"Found matching version for $name: $expectedVersion")
        None
      } else {
        val message = s"Version mismatch for $name: expected $expectedVersion, found $unexpectedVersions"
        log.error(message)
        Some(message)
      }
    }

    val errorMesssages = result.flatten

    if (errorMesssages.isEmpty)
      log.info(s"Version sync check success")
    else
      fail(s"Version sync check failed:\n${errorMesssages.map("  * " + _).mkString("\n")}")
  }

  private def fail(message: String): Nothing = {
    val fail = new MessageOnlyException(message)
    fail.setStackTrace(new Array[StackTraceElement](0))
    throw fail
  }
}
