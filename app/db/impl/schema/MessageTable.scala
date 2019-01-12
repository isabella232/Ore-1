package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.table.ModelTable
import models.project.Message
import models.user.User

class MessageTable(tag: Tag) extends ModelTable[Message](tag, "project_flags") {

  def creatorId   = column[DbRef[User]]("creator_id")
  def allowEdit   = column[Boolean]("allow_edit")
  def isLocalized = column[Boolean]("is_localized")
  def message     = column[String]("message")
  def messageArgs = column[List[String]]("message_args")
  def importance  = column[Int]("importance")

  override def * =
    mkProj((id.?, createdAt.?, creatorId.?, allowEdit, isLocalized, message, messageArgs, importance.?))(
      mkTuple[Message]()
    )
}
