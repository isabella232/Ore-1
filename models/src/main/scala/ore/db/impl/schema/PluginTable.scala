package ore.db.impl.schema

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver
import ore.db.impl.OrePostgresDriver.api._
import ore.models.project.{Asset, Plugin}

class PluginTable(tag: Tag) extends ModelTable[Plugin](tag, "project_asset_plugins") {

  def assetId    = column[DbRef[Asset]]("asset_id")
  def identifier = column[String]("identifier")
  def version    = column[String]("version")

  override def * =
    (
      id.?,
      createdAt.?,
      (
        assetId,
        identifier,
        version
      )
    ).<>(mkApply((Plugin.apply _).tupled), mkUnapply(Plugin.unapply))
}
