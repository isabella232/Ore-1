package models.project

import java.sql.Timestamp

import db.impl.model.common.{Describable, Named}
import db.impl.schema.CompetitionTable
import db.{InsertFunc, Model, ModelQuery, ObjId, ObjectTimestamp}

import slick.lifted.TableQuery

case class Competition(
    id: ObjId[Competition],
    createdAt: ObjectTimestamp,
    name: String,
    description: Option[String],
    startDate: Timestamp,
    endDate: Timestamp,
    isVotingEnabled: Boolean,
    isStaffVotingOnly: Boolean,
    shouldShowVoteCount: Boolean,
    isSpongeOnly: Boolean,
    isSourceRequired: Boolean,
    defaultVotes: Int,
    staffVotes: Int,
    allowedEntries: Int,
    maxEntryTotal: Option[Int]
) extends Model
    with Named
    with Describable {

  override type M = Competition
  override type T = CompetitionTable
}
object Competition {

  def partial(
      name: String,
      description: Option[String],
      startDate: Timestamp,
      endDate: Timestamp,
      isVotingEnabled: Boolean = true,
      isStaffVotingOnly: Boolean = false,
      shouldShowVoteCount: Boolean = true,
      isSpongeOnly: Boolean = false,
      isSourceRequired: Boolean = false,
      defaultVotes: Int = 1,
      staffVotes: Int = 1,
      allowedEntries: Int = 1,
      maxEntryTotal: Option[Int] = None
  ): InsertFunc[Competition] =
    (id, time) =>
      Competition(
        id,
        time,
        name,
        description,
        startDate,
        endDate,
        isVotingEnabled,
        isStaffVotingOnly,
        shouldShowVoteCount,
        isSpongeOnly,
        isSourceRequired,
        defaultVotes,
        staffVotes,
        allowedEntries,
        maxEntryTotal
    )

  implicit val query: ModelQuery[Competition] =
    ModelQuery.from[Competition](TableQuery[CompetitionTable], _.copy(_, _))
}
