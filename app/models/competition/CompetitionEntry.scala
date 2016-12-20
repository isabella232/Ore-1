package models.competition

import db.impl.schema.{CompetitionEntryTable, CompetitionEntryUserVotesTable}
import db.{AssociationQuery, DbRef, InsertFunc, Model, ModelQuery, ModelService, ObjId, ObjectTimestamp}
import models.project.Project
import models.user.User
import ore.project.ProjectOwned
import ore.user.UserOwned

import cats.effect.IO
import slick.lifted.TableQuery

/**
  * Represents a single entry in a [[Competition]].
  *
  * @param id             Unique ID
  * @param createdAt      Instant of creation
  * @param projectId      Project ID
  * @param userId         User owner ID
  * @param competitionId  Competition ID
  */
case class CompetitionEntry(
    id: ObjId[CompetitionEntry],
    createdAt: ObjectTimestamp,
    projectId: DbRef[Project],
    userId: DbRef[User],
    competitionId: DbRef[Competition]
) extends Model {

  override type T = CompetitionEntryTable
  override type M = CompetitionEntry

  /**
    * Returns the [[Competition]] this entry belongs to.
    *
    * @return Competition entry belongs to
    */
  def competition(implicit service: ModelService): IO[Competition] =
    service
      .get[Competition](competitionId)
      .getOrElseF(IO.raiseError(new IllegalStateException("Found competition entry without competition")))
}
object CompetitionEntry {

  def partial(
      projectId: DbRef[Project],
      userId: DbRef[User],
      competitionId: DbRef[Competition]
  ): InsertFunc[CompetitionEntry] = (id, time) => CompetitionEntry(id, time, projectId, userId, competitionId)

  implicit val query: ModelQuery[CompetitionEntry] =
    ModelQuery.from[CompetitionEntry](TableQuery[CompetitionEntryTable], _.copy(_, _))

  implicit val assocEntryVotesQuery: AssociationQuery[CompetitionEntryUserVotesTable, CompetitionEntry, User] =
    AssociationQuery.from[CompetitionEntryUserVotesTable, CompetitionEntry, User](
      TableQuery[CompetitionEntryUserVotesTable]
    )(_.entryId, _.userId)

  implicit val competitionEntryIsUserOwned: UserOwned[CompetitionEntry]       = _.userId
  implicit val competitionEntryIsProjectOwned: ProjectOwned[CompetitionEntry] = _.projectId
}
