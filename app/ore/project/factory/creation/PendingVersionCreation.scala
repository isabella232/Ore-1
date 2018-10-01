package ore.project.factory.creation

import models.project._
import ore.Colors.Color
import ore.project.io.PluginFile
import ore.{Cacheable, Platforms}
import play.api.cache.SyncCacheApi

/**
  * Represents a pending version to be created later.
  *
  * @param project        Project version belongs to
  * @param channelName    Name of channel this version will be in
  * @param channelColor   Color of channel for this version
  * @param underlying     Version that is pending
  * @param plugin         Uploaded plugin
  */
case class PendingVersionCreation(factory: ProjectCreationFactory,
                                  var project: Project,
                                  var channelName: String,
                                  var channelColor: Color,
                                  underlying: Version,
                                  plugin: PluginFile,
                                  var createForumPost: Boolean,
                                  override val cacheApi: SyncCacheApi)
  extends Cacheable {

  override def key: String = this.project.url + '/' + this.underlying.versionString

  def dependenciesAsGhostTags: Seq[Tag] = {
    Platforms.getPlatformGhostTags(this.underlying.dependencies)
  }
}
