/**
 * Copyright (C) 2016-2020 Lightbend Inc. <https://www.lightbend.com>
 */
import sbt.Keys._
import sbt._

/**
 * This plugins adds Akka snapshot repositories when running a nightly build.
 */
object AkkaSnapshotRepositories extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements

  // This is copy & pasted to ScriptedTools so that scripted tests
  // can use Akka snapshot repositories as well. If you change it here, remember to keep
  // ScriptedTools in sync.
  override def projectSettings: Seq[Def.Setting[_]] = {
    // The system property lagom.build.akka.version is regularly used for snapshot versions
    // so it's the trigger for adding the snapshots resolvers
    resolvers ++= (sys.props.get("lagom.build.akka.version") match {
      case Some(_) =>
        Seq(
          "akka-snapshot-repository".at("https://repo.akka.io/snapshots"),
          "akka-http-snapshot-repository".at("https://dl.bintray.com/akka/snapshots/")
        )
      case None => Seq.empty
    })
  }
}
