package sbt

/**
 * Work around sbts private[sbt] on some plugin functions
 */
object PluginsAccessor {

  /**
   * Exclude a plugin
   */
  def exclude(plugin: AutoPlugin): Plugins.Basic = Plugins.Exclude(plugin)
}
