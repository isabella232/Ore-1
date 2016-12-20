package models.competition

import scala.concurrent.duration._
import java.sql.Timestamp
import java.util.Date

import db.impl.model.common.{Describable, Named}
import db.impl.schema.CompetitionTable
import db.impl.OrePostgresDriver.api._
import db.{DbRef, InsertFunc, Model, ModelQuery, ModelService, ObjId, ObjectTimestamp}
import db.access.ModelAccess
import models.user.User
import ore.user.UserOwned
import util.StringUtils.{localDateTime2timestamp, noneIfEmpty}
import form.project.competition.{CompetitionCreateForm, CompetitionSaveForm}

import cats.effect.IO
import slick.lifted.TableQuery

/**
  * Represents a [[models.project.Project]] competition.
  *
  * @param id                  Unique ID
  * @param createdAt           Instant of creation
  * @param userId              Owner ID
  * @param name                Competition name
  * @param description         Competition description
  * @param startDate           Date when the competition begins
  * @param endDate             Date when the competition ends
  * @param timeZone            Time zone of competition
  * @param isVotingEnabled     True if project voting is enabled
  * @param isStaffVotingOnly   True if only staff members can vote
  * @param shouldShowVoteCount True if the vote count should be displayed
  * @param isSpongeOnly        True if only Sponge plugins are permitted in the competition
  * @param isSourceRequired    True if source-code is required for entry to the competition
  * @param defaultVotes        Default amount of votes a user has
  * @param staffVotes          The amount of votes staff-members have
  * @param allowedEntries      The amount of entries a user may submit
  * @param maxEntryTotal       The total amount of projects allowed in the competition
  */
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

  /**
    * Returns this competition's [[CompetitionEntry]]s.
    *
    * @return Competition entries
    */
  def entries(implicit service: ModelService): ModelAccess[CompetitionEntry] =
    service.access[CompetitionEntry](_.competitionId === id.value)

  /**
    * Saves this competition from the submitted form data.
    *
    * @param formData Submitted form data
    */
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

  /**
    * Returns the amount of time remaining in the competition.
    *
    * @return Time remaining in competition
    */
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
