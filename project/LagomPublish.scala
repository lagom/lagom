import Dependencies.{ validateDependencies, validateDependenciesTask }
import sbt.{ Def, FeedbackProvidedException, Resolver, Task, taskKey }
import sbt.Keys.{ name, publishTo, streams }

object LagomPublish {

  val validatePublishSettings = taskKey[Unit]("Validate Lagom settings to publish released artifacts.")

  val validatePublishSettingsTask: Def.Initialize[Task[Unit]] = Def.task {
    val ptValue: Option[Resolver] = publishTo.value

    val log = streams.value.log

    if (ptValue.isEmpty) {
      throw PublishValidationFailed
    }else{
       log.info(s"[${name.value}] PublishTo settings validation passed.")
    }
  }

  val validatePublishSettingsSetting = validatePublishSettings := validatePublishSettingsTask.value

  private object PublishValidationFailed extends RuntimeException with FeedbackProvidedException {
    override def toString = "PublishTo settings validation failed!"
  }

}
