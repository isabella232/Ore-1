package models.admin
import db.impl.schema.ReviewMessageTable
import db.{DbRef, InsertFunc, Model, ModelQuery, ObjId, ObjectTimestamp}
import models.project.Message

import slick.lifted.TableQuery

case class ReviewMessage(
    id: ObjId[ReviewMessage],
    createdAt: ObjectTimestamp,
    reviewId: DbRef[Review],
    messageId: DbRef[Message],
    action: Option[String]
) extends Model {

  override type M = ReviewMessage
  override type T = ReviewMessageTable
}
object ReviewMessage {

  def partial(
      reviewId: DbRef[Review],
      messageId: DbRef[Message],
      action: Option[String] = None
  ): InsertFunc[ReviewMessage] = (id, time) => ReviewMessage(id, time, reviewId, messageId, action)

  implicit val query: ModelQuery[ReviewMessage] =
    ModelQuery.from[ReviewMessage](TableQuery[ReviewMessageTable], _.copy(_, _))
}
