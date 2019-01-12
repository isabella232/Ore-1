package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.table.ModelTable
import models.admin.{Review, ReviewMessage}
import models.project.Message

class ReviewMessageTable(tag: Tag) extends ModelTable[ReviewMessage](tag, "project_notes_messages") {

  def reviewId  = column[DbRef[Review]]("review_id")
  def messageId = column[DbRef[Message]]("message_id")
  def action    = column[String]("action")

  override def * = mkProj((id.?, createdAt.?, reviewId, messageId, action.?))(mkTuple[ReviewMessage]())
}
