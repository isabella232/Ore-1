package ore.db.impl.schema

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.project.{Plugin, PluginPlatform, Version}

class PluginPlatformTable(tag: Tag) extends ModelTable[PluginPlatform](tag, "project_asset_plugin_platforms") {

  def pluginId              = column[DbRef[Plugin]]("plugin_id")
  def platform              = column[String]("platform")
  def platformVersion       = column[Option[String]]("platform_version")
  def platformCoarseVersion = column[Option[String]]("platform_coarse_version")

  override def * =
    (id.?, createdAt.?, (pluginId, platform, platformVersion, platformCoarseVersion)).<>(
      mkApply(
        (PluginPlatform.apply _).tupled
      ),
      mkUnapply(PluginPlatform.unapply)
    )
}
