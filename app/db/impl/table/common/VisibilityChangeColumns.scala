package db.impl.table.common

import java.sql.Timestamp

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.impl.model.common.VisibilityChange
import db.table.ModelTable
import models.project.{Message, Visibility}
import models.user.User

trait VisibilityChangeColumns[M <: VisibilityChange] extends ModelTable[M] {

  def createdBy  = column[DbRef[User]]("created_by")
  def messageId  = column[DbRef[Message]]("message_id")
  def resolvedAt = column[Timestamp]("resolved_at")
  def resolvedBy = column[DbRef[User]]("resolved_by")
  def visibility = column[Visibility]("visibility")
}
