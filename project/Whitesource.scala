import sbt.Keys._
import sbt._
import sbtwhitesource.WhiteSourcePlugin
import sbtwhitesource.WhiteSourcePlugin.autoImport._

object Whitesource extends AutoPlugin {
  override def requires: Plugins = WhiteSourcePlugin

  override def trigger = allRequirements

  override lazy val projectSettings = Seq(
    whitesourceProduct := "Lightbend Reactive Platform",
    whitesourceAggregateProjectName := {
      (moduleName in LocalRootProject).value + "-" +
        whitesourceProjectSuffix(isSnapshot.value, currentBranch.value, (version in LocalRootProject).value)
    },
    whitesourceForceCheckAllDependencies := true,
    whitesourceFailOnError := true
  )

  private lazy val currentBranch = Def.setting {
    sys.env.getOrElse("CURRENT_BRANCH", "")
  }

  private val StableBranch = """(\d+)\.(\d+)\.x""".r
  private val FinalVersion = """(\d+)\.(\d+)\.(\d+)""".r // no -M1, -RC1, etc. suffix

  def whitesourceProjectSuffix(isSnapshot: Boolean, currentBranch: String, version: String): String = {
    if (isSnapshot) {
      // There are three scenarios:
      currentBranch match {
        // 1. It is the master branch
        case "master" => "master"
        // 2. It is a stable branch (1.3.x, 1.4.x, etc.)
        case StableBranch(major, minor) => s"$major.$minor-snapshot"
        // 3. It is some other branch
        case _ => "adhoc"
      }
    } else {
      // Building a release tag
      version match {
        case FinalVersion(major, minor, _) => s"$major.$minor-stable"
        // Milestone or RC
        case _ => "adhoc"
      }
    }
  }
}
