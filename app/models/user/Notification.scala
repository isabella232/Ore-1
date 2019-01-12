package models.user

import db.impl.access.UserBase
import db.impl.schema.NotificationTable
import db.{DbRef, InsertFunc, Model, ModelQuery, ObjId, ObjectTimestamp}
import models.project.Message
import ore.user.UserOwned
import ore.user.notification.NotificationType

import cats.effect.IO
import slick.lifted.TableQuery

/**
  * Represents a [[User]] notification.
  *
  * @param id               Unique ID
  * @param createdAt        Instant of cretion
  * @param userId           ID of User this notification belongs to
  * @param notificationType Type of notification
  * @param messageId        The message to display
  * @param action           Action to perform on click
  * @param isRead             True if notification has been read
  */
case class Notification(
    id: ObjId[Notification],
    createdAt: ObjectTimestamp,
    userId: DbRef[User],
    originId: DbRef[User],
    notificationType: NotificationType,
    messageId: DbRef[Message],
    action: Option[String],
    isRead: Boolean
) extends Model {

  override type M = Notification
  override type T = NotificationTable

  /**
    * Returns the [[User]] from which this Notification originated from.
    *
    * @return User from which this originated from
    */
  def origin(implicit userBase: UserBase): IO[User] =
    userBase.get(this.originId).getOrElse(throw new NoSuchElementException("Get on None")) // scalafix:ok
}
object Notification {
  def partial(
      userId: DbRef[User],
      originId: DbRef[User],
      notificationType: NotificationType,
      messageId: DbRef[Message],
      action: Option[String] = None,
      isRead: Boolean = false
  ): InsertFunc[Notification] =
    (id, time) => Notification(id, time, userId, originId, notificationType, messageId, action, isRead)

  implicit val query: ModelQuery[Notification] =
    ModelQuery.from[Notification](TableQuery[NotificationTable], _.copy(_, _))

  implicit val isUserOwned: UserOwned[Notification] = (a: Notification) => a.userId
}
