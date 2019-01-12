package db.impl.schema

import db.DbRef
import db.impl.OrePostgresDriver.api._
import db.table.ModelTable
import models.project.Message
import models.user.{Notification, User}
import ore.user.notification.NotificationType

class NotificationTable(tag: Tag) extends ModelTable[Notification](tag, "notifications") {

  def userId           = column[DbRef[User]]("user_id")
  def originId         = column[DbRef[User]]("origin_id")
  def notificationType = column[NotificationType]("notification_type")
  def messageId        = column[DbRef[Message]]("message_id")
  def action           = column[String]("action")
  def read             = column[Boolean]("read")

  override def * =
    mkProj((id.?, createdAt.?, userId, originId, notificationType, messageId, action.?, read))(
      mkTuple[Notification]()
    )
}
