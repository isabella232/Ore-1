package ore.db.impl.schema

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver
import ore.db.impl.OrePostgresDriver.api._
import ore.models.project.{Asset, Plugin}

class PluginTable(tag: Tag) extends ModelTable[Plugin](tag, "project_version_plugins") {

  implicit private val listOptionStrType: OrePostgresDriver.DriverJdbcType[List[Option[String]]] =
    new OrePostgresDriver.SimpleArrayJdbcType[String]("text")
      .mapTo[Option[String]](Option(_), _.orNull)
      .to(_.toList)

  def assetId            = column[DbRef[Asset]]("asset_id")
  def pluginId           = column[String]("plugin_id")
  def version            = column[String]("version")
  def dependencyIds      = column[List[String]]("version")
  def dependencyVersions = column[List[Option[String]]]("version")

  override def * =
    (
      id.?,
      createdAt.?,
      (
        assetId,
        pluginId,
        version,
        dependencyIds,
        dependencyVersions
      )
    ) <> (mkApply((Plugin.apply _).tupled), mkUnapply(Plugin.unapply))
}
