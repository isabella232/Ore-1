package models.project

import db.impl.schema.ProjectNoteTable
import db.{DbRef, InsertFunc, Model, ModelQuery, ObjId, ObjectTimestamp}

import slick.lifted.TableQuery

case class ProjectNote(
    id: ObjId[ProjectNote],
    createdAt: ObjectTimestamp,
    projectId: DbRef[Project],
    messageId: DbRef[Message]
) extends Model {

  override type M = ProjectNote
  override type T = ProjectNoteTable
}
object ProjectNote {

  def partial(
      projectId: DbRef[Project],
      messageId: DbRef[Message]
  ): InsertFunc[ProjectNote] = (id, time) => ProjectNote(id, time, projectId, messageId)

  implicit val query: ModelQuery[ProjectNote] =
    ModelQuery.from[ProjectNote](TableQuery[ProjectNoteTable], _.copy(_, _))
}
