/**
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */
import sbt.Keys._
import sbt._

/**
 * This plugins adds Akka snapshot repositories when running a nightly build.
 */
object AkkaSnapshotRepositories extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  override def projectSettings: Seq[Def.Setting[_]] = {
    // The system property lagom.build.akka.version is regularly used for snapshot versions
    // so it's the trigger for adding the snapshots resolvers
    resolvers ++= (sys.props.get("lagom.build.akka.version") match {
      case Some(_) => Seq("akka-snapshot-repository".at("https://repo.akka.io/snapshots"))
      case None    => Seq.empty
    })
  }
}
