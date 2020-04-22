package ore.db.impl.schema

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.table.common.VisibilityColumn
import ore.models.project.{Asset, Project, Version}

class AssetTable(tag: Tag) extends ModelTable[Asset](tag, "project_version_assets") with VisibilityColumn[Asset] {

  def projectId = column[DbRef[Project]]("project_id")
  def versionId = column[DbRef[Version]]("version_id")
  def filename  = column[String]("filename")
  def isMain    = column[Boolean]("is_main")
  def assetType = column[Asset.AssetType]("asset_type")
  def hash      = column[String]("hash")
  def fileSize  = column[Long]("filesize")
  def usesMixin = column[Boolean]("uses_mixin")

  override def * =
    (
      id.?,
      createdAt.?,
      (
        projectId,
        versionId,
        filename,
        isMain,
        assetType,
        hash,
        fileSize,
        visibility,
        usesMixin
      )
    ) <> (mkApply((Asset.apply _).tupled), mkUnapply(Asset.unapply))
}
