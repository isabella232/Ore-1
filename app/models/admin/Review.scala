package models.admin

import java.sql.Timestamp

import db.impl.schema.ReviewTable
import db.{DbRef, InsertFunc, Model, ModelQuery, ObjId, ObjectTimestamp}
import models.project.{Project, Version}
import models.user.User

import slick.lifted.TableQuery

/**
  * Represents an approval instance of [[Project]] [[Version]].
  *
  * @param id           Unique ID
  * @param createdAt    When it was created
  * @param versionId    User who is approving
  * @param userId       User who is approving
  * @param endedAt      When the approval process ended
  */
case class Review(
    id: ObjId[Review],
    createdAt: ObjectTimestamp,
    versionId: DbRef[Version],
    userId: DbRef[User],
    endedAt: Option[Timestamp]
) extends Model {

  /** Self referential type */
  override type M = Review

  /** The model's table */
  override type T = ReviewTable
}

object Review {
  def partial(
      versionId: DbRef[Version],
      userId: DbRef[User],
      endedAt: Option[Timestamp]
  ): InsertFunc[Review] = (id, time) => Review(id, time, versionId, userId, endedAt)

  def ordering: Ordering[(Review, _)] =
    // TODO make simple + check order
    Ordering.by(_._1.createdAt.value.getTime)

  def ordering2: Ordering[Review] =
    // TODO make simple + check order
    Ordering.by(_.createdAt.value.getTime)

  implicit val query: ModelQuery[Review] =
    ModelQuery.from[Review](TableQuery[ReviewTable], _.copy(_, _))
}
