package ore.db.impl.schema

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.project.{Asset, Project}

class AssetTable(tag: Tag) extends ModelTable[Asset](tag, "project_assets") {

  def projectId = column[DbRef[Project]]("project_id")
  def filename  = column[String]("filename")
  def hash      = column[String]("hash")
  def fileSize  = column[Long]("filesize")

  override def * =
    (
      id.?,
      createdAt.?,
      (
        projectId,
        filename,
        hash,
        fileSize
      )
    ).<>(mkApply((Asset.apply _).tupled), mkUnapply(Asset.unapply))
}
