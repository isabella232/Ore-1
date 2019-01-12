package models.project

import play.api.i18n.Messages

import db.impl.schema.MessageTable
import db.{DbRef, InsertFunc, Model, ModelQuery, ObjId, ObjectTimestamp}
import models.user.User

import slick.lifted.TableQuery

case class Message(
    id: ObjId[Message],
    createdAt: ObjectTimestamp,
    creatorId: Option[DbRef[User]],
    allowEdit: Boolean,
    isLocalized: Boolean,
    message: String,
    messageArgs: List[String],
    importance: Option[Int]
) extends Model {

  override type M = Message
  override type T = MessageTable

  def format(implicit messages: Messages): String = if (isLocalized) messages(message, messageArgs: _*) else message
}
object Message {
  def partial(
      creatorId: Option[DbRef[User]],
      allowEdit: Boolean,
      isLocalized: Boolean,
      message: String,
      messageArgs: List[String],
      importance: Option[Int]
  ): InsertFunc[Message] =
    (id, createdAt) => Message(id, createdAt, creatorId, allowEdit, isLocalized, message, messageArgs, importance)

  implicit val query: ModelQuery[Message] =
    ModelQuery.from[Message](TableQuery[MessageTable], _.copy(_, _))
}
