package ore.db.impl.schema

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.project.{Dependency, Plugin}

class DependencyTable(tag: Tag) extends ModelTable[Dependency](tag, "project_asset_plugin_dependencies") {

  def pluginId      = column[DbRef[Plugin]]("plugin_id")
  def identifier    = column[String]("identifier")
  def versionRange  = column[String]("version_range")
  def versionSyntax = column[Dependency.VersionSyntax]("version_syntax")

  override def * =
    (
      id.?,
      createdAt.?,
      (
        pluginId,
        identifier,
        versionRange.?,
        versionSyntax.?
      )
    ).<>(mkApply((Dependency.apply _).tupled), mkUnapply(Dependency.unapply))
}
