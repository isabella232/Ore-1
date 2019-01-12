package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.table.ModelTable
import models.project.{Message, Project, ProjectNote}

class ProjectNoteTable(tag: Tag) extends ModelTable[ProjectNote](tag, "project_notes_messages") {

  def projectId = column[DbRef[Project]]("project_id")
  def messageId = column[DbRef[Message]]("message_id")

  override def * = mkProj((id.?, createdAt.?, projectId, messageId))(mkTuple[ProjectNote]())
}
