package db.impl.schema

import java.sql.Timestamp

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.table.ModelTable
import models.project.{Flag, Message, Project}
import models.user.User
import ore.project.FlagReason

class FlagTable(tag: Tag) extends ModelTable[Flag](tag, "project_flags") {

  def projectId  = column[DbRef[Project]]("project_id")
  def userId     = column[DbRef[User]]("user_id")
  def reason     = column[FlagReason]("reason")
  def messageId  = column[DbRef[Message]]("message_id")
  def isResolved = column[Boolean]("is_resolved")
  def resolvedAt = column[Timestamp]("resolved_at")
  def resolvedBy = column[DbRef[User]]("resolved_by")

  override def * =
    mkProj((id.?, createdAt.?, projectId, userId, reason, messageId, isResolved, resolvedAt.?, resolvedBy.?))(
      mkTuple[Flag]()
    )
}
