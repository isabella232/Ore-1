package models.project

import scala.concurrent.duration._
import java.sql.Timestamp
import java.util.Date

import db.impl.model.common.{Describable, Named}
import db.impl.schema.CompetitionTable
import db.{DbRef, InsertFunc, Model, ModelQuery, ModelService, ObjId, ObjectTimestamp}
import models.user.User
import ore.user.UserOwned
import util.StringUtils.{localDateTime2timestamp, noneIfEmpty}
import form.project.competition.{CompetitionCreateForm, CompetitionSaveForm}

import cats.effect.IO
import slick.lifted.TableQuery

case class Competition(
    id: ObjId[Competition],
    createdAt: ObjectTimestamp,
    userId: DbRef[User],
    name: String,
    description: Option[String],
    startDate: Timestamp,
    endDate: Timestamp,
    timeZone: String,
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

  def save(formData: CompetitionSaveForm)(implicit service: ModelService): IO[Competition] = service.update(
    copy(
      startDate = localDateTime2timestamp(formData.startDate, formData.timeZoneId),
      endDate = localDateTime2timestamp(formData.endDate, formData.timeZoneId),
      isVotingEnabled = formData.isVotingEnabled,
      isStaffVotingOnly = formData.isStaffVotingOnly,
      shouldShowVoteCount = formData.shouldShowVoteCount,
      isSourceRequired = formData.isSourceRequired,
      defaultVotes = formData.defaultVotes,
      staffVotes = formData.staffVotes,
      allowedEntries = formData.allowedEntries,
      maxEntryTotal = Some(formData.maxEntryTotal).filter(_ != -1)
    )
  )

  def timeRemaining: FiniteDuration = (this.endDate.getTime - new Date().getTime).millis
}
object Competition {

  def partial(
      name: String,
      description: Option[String] = None,
      userId: DbRef[User],
      startDate: Timestamp,
      endDate: Timestamp,
      timeZone: String,
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
        userId,
        name,
        description,
        startDate,
        endDate,
        timeZone,
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

  def partial(user: User, formData: CompetitionCreateForm): InsertFunc[Competition] =
    partial(
      userId = user.id.value,
      name = formData.name.trim,
      description = formData.description.flatMap(noneIfEmpty),
      startDate = localDateTime2timestamp(formData.startDate, formData.timeZoneId),
      endDate = localDateTime2timestamp(formData.endDate, formData.timeZoneId),
      timeZone = formData.timeZoneId,
      isVotingEnabled = formData.isVotingEnabled,
      isStaffVotingOnly = formData.isStaffVotingOnly,
      shouldShowVoteCount = formData.shouldShowVoteCount,
      isSpongeOnly = formData.isSpongeOnly,
      isSourceRequired = formData.isSourceRequired,
      defaultVotes = formData.defaultVotes,
      staffVotes = formData.staffVotes,
      allowedEntries = formData.allowedEntries,
      maxEntryTotal = Some(formData.maxEntryTotal).filter(_ != -1)
    )

  implicit val query: ModelQuery[Competition] =
    ModelQuery.from[Competition](TableQuery[CompetitionTable], _.copy(_, _))

  implicit val competitionIsUserOwned: UserOwned[Competition] = _.userId
}
