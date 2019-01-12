package models.admin

import java.sql.Timestamp

import db.impl.model.common.VisibilityChange
import db.impl.schema.VersionVisibilityChangeTable
import db.{DbRef, InsertFunc, Model, ModelQuery, ObjId, ObjectTimestamp}
import models.project.{Version, Visibility}
import models.user.User

import slick.lifted.TableQuery

case class VersionVisibilityChange(
    id: ObjId[VersionVisibilityChange],
    createdAt: ObjectTimestamp,
    createdBy: Option[DbRef[User]],
    versionId: DbRef[Version],
    messageId: DbRef[Message],
    resolvedAt: Option[Timestamp],
    resolvedBy: Option[DbRef[User]],
    visibility: Visibility
) extends Model
    with VisibilityChange {

  /** Self referential type */
  override type M = VersionVisibilityChange

  /** The model's table */
  override type T = VersionVisibilityChangeTable)
}
object VersionVisibilityChange {
  def partial(
      createdBy: Option[DbRef[User]] = None,
      versionId: DbRef[Version],
      messageId: DbRef[Message],
      resolvedAt: Option[Timestamp] = None,
      resolvedBy: Option[DbRef[User]] = None,
      visibility: Visibility = Visibility.New
  ): InsertFunc[VersionVisibilityChange] =
    (id, time) => VersionVisibilityChange(id, time, createdBy, versionId, messageId, resolvedAt, resolvedBy, visibility)

  implicit val query: ModelQuery[VersionVisibilityChange] =
    ModelQuery.from[VersionVisibilityChange](TableQuery[VersionVisibilityChangeTable], _.copy(_, _))
}
