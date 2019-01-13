package form.project.competition

import java.time.{LocalDateTime, ZoneId}

trait CompetitionData {

  val startDate: LocalDateTime
  val endDate: LocalDateTime
  val timeZoneId: String
  val isVotingEnabled: Boolean
  val isStaffVotingOnly: Boolean
  val shouldShowVoteCount: Boolean
  val isSourceRequired: Boolean
  val defaultVotes: Int
  val staffVotes: Int
  val allowedEntries: Int
  val maxEntryTotal: Int
  val timeZone: ZoneId = ZoneId.of(this.timeZoneId)

  def checkDates(checkStart: Boolean): Boolean =
    (startDate.isAfter(LocalDateTime.now(this.timeZone)) || !checkStart) && startDate.isBefore(this.endDate)

}
