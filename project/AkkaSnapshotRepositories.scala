/**
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */
import sbt.Keys._
import sbt._

/**
 * This plugins adds Akka snapshot repositories when running a nightly build.
 */
object AkkaSnapshotRepositories extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  override def projectSettings: Seq[Def.Setting[_]] = {
    // If this is a cron job in Travis:
    // https://docs.travis-ci.com/user/cron-jobs/#detecting-builds-triggered-by-cron
    resolvers ++= (sys.env.get("TRAVIS_EVENT_TYPE").filter(_.equalsIgnoreCase("cron")) match {
      case Some(_) => Seq("akka-snapshot-repository" at "https://repo.akka.io/snapshots")
      case None => Seq.empty
    })
  }
}
